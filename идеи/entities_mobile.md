**Для реализации Offline-First на Android оптимально использовать 5 локальных таблиц (Room Entities), которые разделяют справочники с сервера (шаблоны) и локальные рабочие данные (визиты, ответы, очередь фото) со статусами синхронизации.**

Такая структура позволяет UI подписываться на изменения через Kotlin `Flow` и гарантирует, что фоновый `WorkManager` точно знает, какие файлы еще не ушли в S3, а какие отчеты ждут отправки на бэкенд.

1. Схема таблиц Room (Kotlin Entities)

1.1. `TemplateEntity` и `TemplateFieldEntity` (Кэш шаблонов)

Эти таблицы заполняются исключительно из ответа сервера (`POST /mobile/sync`). Инспектор не может их редактировать, только читать.

```
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String, // UUID с сервера
    val name: String,
    val description: String?,
    val version: Int
)

@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateFieldEntity(
    @PrimaryKey val id: String, // UUID пункта
    val templateId: String,
    val orderIndex: Int,
    val title: String,
    val fieldType: String, // "TEXT", "RATING_1_5", "YES_NO", "SELECT"
    val optionsJson: String?, // JSON-строка для вариантов SELECT
    val isRequired: Boolean,
    val requirePhotoOnIssue: Boolean
)
```

1.2. `VisitEntity` (Выезды на объекты)

Обратите внимание: данные об объекте (`locationName`, `locationAddress` и др.) лучше денормализовать и хранить прямо внутри `VisitEntity`, чтобы не усложнять локальную БД лишними джойнами.

```
@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val templateId: String,
    
    // Денормализованные данные объекта (приходят с сервера)
    val locationName: String,
    val locationAddress: String,
    val targetLat: Double?,
    val targetLon: Double?,
    
    // Временные метки (ISO-8601 или Epoch Millis)
    val scheduledAt: Long,
    val completedAt: Long? = null,
    
    // Фактические координаты при закрытии
    val factLat: Double? = null,
    val factLon: Double? = null,
    
    // Бизнес-статус визита: "PLANNED", "IN_PROGRESS", "COMPLETED", "OVERDUE"
    val status: String,
    
    // Технический статус для WorkManager: "SYNCED", "PENDING_SYNC"
    val syncStatus: String = "SYNCED"
)
```

1.3. `VisitAnswerEntity` (Ответы инспектора)

Создается локально при заполнении чек-листа. `id` генерируется на телефоне (`UUID.randomUUID().toString()`).

```
@Entity(
    tableName = "visit_answers",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Уникальный индекс гарантирует 1 ответ на 1 вопрос в рамках визита
    indices = [Index(value = ["visitId", "fieldId"], unique = true)]
)
data class VisitAnswerEntity(
    @PrimaryKey val id: String, // UUIDv4, сгенерированный на клиенте
    val visitId: String,
    val fieldId: String,
    val value: String?, // Текст, цифра оценки или "YES"/"NO"
    val hasIssue: Boolean = false,
    val comment: String? = null
)
```


1.4. `MediaFileEntity` (Очередь фотографий)

Управляет жизненным циклом снимка: от сохранения в память телефона до загрузки в S3 и удаления локального файла.

```
@Entity(
    tableName = "media_files",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("visitId"), Index("answerId")]
)
data class MediaFileEntity(
    @PrimaryKey val id: String, // UUIDv4, сгенерированный при съемке
    val visitId: String,
    val answerId: String?, // К какому именно ответу прикреплено фото
    val localFilePath: String, // Путь в context.filesDir/pending_photos/...
    val s3Key: String? = null, // Заполняется после получения Presigned URL
    val fileType: String = "image/jpeg",
    val createdAt: Long,
    
    // Статус загрузки файла: "NOT_UPLOADED", "UPLOADED_TO_S3"
    val uploadStatus: String = "NOT_UPLOADED"
)
```

2. Как это работает в связке (Жизненный цикл данных)

3. **Автосохранение черновика:**  
    Когда инспектор нажимает кнопку ответа в чек-листе, UI вызывает `dao.upsertAnswer(answer)`. Данные мгновенно сохраняются в `visit_answers`, а `visits.status` меняется на `IN_PROGRESS`. При этом `visits.syncStatus` остается `SYNCED` (или `DRAFT`), чтобы `WorkManager` не пытался отправить незаконченный визит раньше времени.
4. **Закрытие визита:**  
    Инспектор нажимает «Завершить визит». Приложение записывает GPS-координаты, ставит `visits.status = "COMPLETED"` и `visits.syncStatus = "PENDING_SYNC"`. После этого запускается `WorkManager`.
5. **Работа `SyncWorker` по шагам:**
    - Делает выборку: `SELECT * FROM media_files WHERE uploadStatus = 'NOT_UPLOADED'`.
    - Загружает найденные файлы в S3, записывает полученный `s3Key` и ставит `uploadStatus = 'UPLOADED_TO_S3'`.
    - Выбирает готовые к отправке визиты: `SELECT * FROM visits WHERE syncStatus = 'PENDING_SYNC'` вместе с их ответами и медиафайлами.
    - Отправляет их в `POST /mobile/sync`.
    - После получения `200 OK` переводит `visits.syncStatus = 'SYNCED'` и удаляет физические файлы по `localFilePath`, чтобы не забивать память смартфона.


3. Полезная связка для UI (`@Embedded` + `@Relation`)

Чтобы экран прохождения чек-листа получал все данные одним реактивным потоком `Flow`, в Room удобно описать класс-проекцию:

```
data class VisitWithChecklist(
    @Embedded val visit: VisitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "visitId"
    )
    val answers: List<VisitAnswerEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "visitId"
    )
    val mediaFiles: List<MediaFileEntity>
)
```

Подписавшись на , экран автоматически перерисует карточку вопроса, как только в базу добавится новое фото или обновится текст комментария.
