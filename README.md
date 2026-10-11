# LilConfig

Типизированные YAML-конфиги для Bukkit/Spigot/Paper-плагинов на **Java 21+**.
Описываешь настройки обычным классом, а LilConfig создаёт файл, загружает поля и сохраняет изменения.
Наследоваться от специального класса или писать сериализатор для каждой модели не нужно.

```java
var config = LilConfig.provider(plugin).load(Settings.class);
Settings settings = config.get();

settings.maxPlayers = 50;
config.save();

config.reload();
int limit = config.get().maxPlayers;
```

## Быстрый старт

```java
import by.likebebras.lilconfig.annotation.*;
import org.bukkit.Material;
import java.time.Duration;
import java.util.*;

@ConfigFile("config.yml")
@Comment({"Настройки плагина", "Изменения применяются после reload."})
public class Settings {
    @Key("max-players")
    @Comment("Максимальное количество игроков")
    @Range(min = 1, max = 500)
    public int maxPlayers = 20;

    public Duration cooldown = Duration.ofSeconds(30);
    public Set<Material> allowedBlocks = EnumSet.of(Material.STONE, Material.DIRT);
    public Map<String, Group> groups = new LinkedHashMap<>(Map.of("vip", new Group()));
    public Messages messages = new Messages();

    public static class Group {
        public double multiplier = 2.0;
        public Set<String> permissions = new LinkedHashSet<>(Set.of("plugin.fly"));
    }

    public static class Messages {
        public String welcome = "Добро пожаловать!";
        public List<String> help = new ArrayList<>(List.of("/help", "/spawn"));
    }
}
```

Первый `load()` создаст `plugins/ИмяПлагина/config.yml` с начальными значениями:

```yaml
# Настройки плагина
# Изменения применяются после reload.

# Максимальное количество игроков
max-players: 20
cooldown: PT30S
allowedBlocks:
- STONE
- DIRT
groups:
  vip:
    multiplier: 2.0
    permissions:
    - plugin.fly
messages:
  welcome: Добро пожаловать!
  help:
  - /help
  - /spawn
```

Использование в плагине:

```java
import by.likebebras.lilconfig.Config;
import by.likebebras.lilconfig.ConfigException;
import by.likebebras.lilconfig.LilConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private Config<Settings> settings;

    @Override
    public void onEnable() {
        settings = LilConfig.provider(this).load(Settings.class);
    }

    public boolean reloadSettings() {
        try {
            settings.reload();
            return true;
        } catch (ConfigException exception) {
            getLogger().warning(exception.getMessage());
            return false; // Предыдущая конфигурация продолжает работать.
        }
    }

    public int maxPlayers() {
        return settings.get().maxPlayers;
    }
}
```

## Файлы и значения по умолчанию

```java
var provider = LilConfig.provider(plugin);
var main = provider.load(Settings.class); // @ConfigFile, иначе settings.yml
var arena = provider.load("arenas/desert.yml", Settings.class);
var custom = provider.load("custom.yml", Settings.class, () -> {
    var defaults = new Settings();
    defaults.maxPlayers = 100;
    return defaults;
});
```

Без `@ConfigFile` имя класса преобразуется в kebab-case: `DatabaseSettings` → `database-settings.yml`.
Каждый вызов `load()` создаёт отдельный handle; сохрани его в поле плагина.
Для одного файла обычно нужен один handle. Относительные пути разрешены только внутри папки provider.
Для тестов есть `LilConfig.provider(Path directory)`.

- Отсутствующий файл создаётся автоматически, включая подкаталоги.
- Отсутствующее поле получает значение из конструктора/инициализатора или supplier.
- Вложенные объекты дополняются по полям. Присутствующие `Map`, `List`, `Set` и массивы заменяют коллекцию целиком: `{}` и `[]` действительно очищают её.
- `load()`/`reload()` существующего файла не переписывают его. Вызови `save()`, чтобы дописать новые поля со значениями по умолчанию.
- Неизвестные поля сохраняются в корневом и вложенных объектах. Внутри заменяемых коллекций/словарей содержимое определяется текущей моделью.
- `reload()` создаёт новый объект. Старые ссылки на `config.get()` остаются старыми; получай актуальный объект через handle.
- `save()` перечитывает файл для сохранения неизвестных полей и комментариев, затем записывает известные поля из памяти. Для применения ручных изменений сначала вызови `reload()`.
- Запись идёт через временный файл рядом с конфигом и атомарную замену, если файловая система её поддерживает. При отсутствии поддержки используется обычная замена.

## Поддерживаемые типы

| Тип | Представление / поведение |
| --- | --- |
| Примитивы, обёртки, `String`, `char` | YAML scalar, строгая проверка типа и числового диапазона |
| `BigInteger`, `BigDecimal` | Большие целые; `BigDecimal` записывается строкой для сохранения точности |
| `enum`, включая `Material` | Имя константы; загрузка без учёта регистра |
| `UUID`, `Duration`, `Instant`, `URI` | Строка; время и длительность в ISO-8601 (`PT30S`, `PT5M`) |
| POJO и Java records | YAML mapping; вложенность и generic-поля |
| `List<T>`, `Set<T>`, `Collection<T>` | YAML list; по умолчанию `ArrayList` / `LinkedHashSet` |
| `HashMap<K,V>`, `Map<K,V>` | YAML mapping; для интерфейса создаётся `LinkedHashMap` |
| `EnumSet<E>`, `EnumMap<E,V>` | Коллекции enum |
| `SortedSet`, `NavigableSet`, `SortedMap`, `NavigableMap` | `TreeSet` / `TreeMap` с естественным порядком |
| `Queue<T>`, `Deque<T>` | `ArrayDeque` |
| Массивы, включая `int[]` | YAML list |
| `Optional<T>` | Значение или отсутствие поля |
| Bukkit `ConfigurationSerializable` | Стандартная сериализация Bukkit (`Vector`, `ItemStack`, `Location` и др.) |
| `Object` | Только дерево YAML: строки, числа, boolean, null, списки и словари со строковыми ключами |

Вложенные типы поддерживаются, например:

```java
Map<UUID, List<Reward>> rewards;
HashMap<String, Set<Material>> blocks;
Map<String, List<Map<Material, Set<Integer>>>> complex;
```

Ключи словарей: `String`, числа, boolean, char, `UUID`, enum. Точки в ключах — буквальные символы,
поэтому `Map.of("my.permission", true)` не превращается во вложенную секцию.
Ключи, совпавшие после преобразования (например, `"01"` и `"1"` для `Map<Integer, ...>`), вызывают ошибку.

Обычные классы должны иметь конструктор без аргументов; он может быть `private`.
Поля тоже могут быть приватными, поддерживаются поля предков и конкретные generic-аргументы.
Вложенные классы должны быть `static`. Поля POJO не должны быть `final`: для неизменяемых моделей используй record.
Другие конкретные реализации коллекций поддерживаются при наличии конструктора без аргументов;
собственные компараторы не сохраняются. Сырые `List`/`Map` без generic-аргументов не поддерживаются.

## Records

```java
public record Database(
    @Required String host,
    @Range(min = 1, max = 65535) int port,
    @Key("pool-size") @Comment("Количество соединений") int poolSize
) {}

var database = provider.load("database.yml", Database.class,
        () -> new Database("localhost", 5432, 10));

int port = database.get().port();
database.set(new Database("db.internal", 5432, 20)).save();
```

Supplier задаёт начальные значения и вызывается при каждой загрузке. Без supplier record можно загрузить,
если файл содержит все компоненты. Для отсутствующего файла или компонента будет понятная ошибка.
Records могут находиться внутри обычных классов, списков и словарей.

## Аннотации и валидация

| Аннотация | Назначение |
| --- | --- |
| `@ConfigFile("config.yml")` | Имя файла на корневом классе |
| `@Key("max-players")` | Буквальное имя YAML-ключа поля/компонента record |
| `@Comment({"Строка 1", "Строка 2"})` | Заголовок корневого класса или комментарий поля |
| `@Ignore` | Пропуск поля POJO |
| `@Required` | Запрет null и пустого `Optional` после применения defaults |
| `@Range(min = 1, max = 100)` | Включительные числовые границы |

`static`, `transient` и синтетические поля автоматически пропускаются. `@Ignore` не применяется к record-компонентам.
Комментарии аннотаций заменяют комментарий соответствующего поля; остальные комментарии Bukkit сохраняет.
Комментарии внутри объектов, находящихся в списках или словарях, из аннотаций не генерируются.
Сохранение нормализует формат YAML, поэтому исходные кавычки, отступы и форматирование не гарантируются.

Для проверки нескольких полей реализуй `Validatable`:

```java
public class Limits implements by.likebebras.lilconfig.Validatable {
    public int min = 1;
    public int max = 10;

    @Override
    public void validate() {
        if (min > max) throw new IllegalArgumentException("min не может быть больше max");
    }
}
```

Валидация применяется к корневым и вложенным объектам при загрузке, `set()` и сохранении.
Она должна быть без побочных эффектов: библиотека может вызывать её несколько раз.
Ошибка содержит имя файла и путь значения, например `$.groups[vip].multiplier`.
Повреждённый YAML, дубликаты ключей, циклические ссылки, несовместимые типы и переполнения чисел не игнорируются.
Неудачный `reload()` оставляет предыдущий объект в памяти. Неудачная проверка перед `save()` не меняет файл;
изменённый пользователем объект при этом остаётся изменённым.

## Форматирование YAML

Все настройки необязательны. Без новых аннотаций сохраняется прежний формат вывода.
Форматирование применяется при создании файла и `save()`; `load()`/`reload()` существующего файла его не переписывают.

```java
import by.likebebras.lilconfig.*;
import by.likebebras.lilconfig.annotation.*;
import java.util.List;

@ConfigFile("config.yml")
@KeysFormat(Format.KEBAB_CASE)
@KeysSpacing(1)
@KeysOrder(KeyOrder.DECLARATION)
@YamlIndent(2)
@Header({"Настройки сервера", "После изменений выполни reload."})
@Footer("Конец конфигурации")
public class Settings {
    @Order(-10)
    @InlineComment("От 1 до 500")
    public int maxPlayers = 20;

    @Comment("Сообщение при входе")
    @StringStyle(StringFormat.DOUBLE_QUOTED)
    public String welcomeMessage = "Привет!";

    @ListStyle(CollectionStyle.FLOW)
    public List<String> enabledWorlds = List.of("world", "world_nether");

    @StringStyle(StringFormat.LITERAL)
    public String helpText = "/help — помощь\n/spawn — на спавн";
}
```

```yaml
# Настройки сервера
# После изменений выполни reload.

max-players: 20 # От 1 до 500

# Сообщение при входе
welcome-message: "Привет!"

enabled-worlds: [world, world_nether]

help-text: |-
  /help — помощь
  /spawn — на спавн

# Конец конфигурации
```

| Аннотация | Где применяется | Назначение |
| --- | --- | --- |
| `@KeysFormat(Format.KEBAB_CASE)` | Класс / record | Преобразование имён полей в ключи при чтении и записи |
| `@KeysSpacing(1)` | Класс, поле, компонент record | Число пустых строк между соседними ключами секции: `0..10` |
| `@KeysOrder(KeyOrder.ALPHABETICAL)` | Класс, поле, компонент record | Порядок ключей секций и словарей |
| `@Order(-10)` | Поле / компонент record | Приоритет поля при записи: меньше — раньше; по умолчанию `0` |
| `@SpaceBefore(2)` | Поле / компонент record | Минимум пустых строк перед комментарием и ключом: `0..10` |
| `@SpaceAfter(1)` | Поле / компонент record | Минимум пустых строк после значения: `0..10` |
| `@StringStyle(StringFormat.DOUBLE_QUOTED)` | Класс, поле, компонент record | Кавычки или многострочный стиль строковых значений |
| `@ListStyle(CollectionStyle.FLOW)` | Класс, поле, компонент record | Списки: `FLOW` (`[a, b]`) или `BLOCK` (строки с `-`) |
| `@MapStyle(CollectionStyle.FLOW)` | Класс, поле, компонент record | Объекты и словари: `FLOW` (`{a: 1}`) или `BLOCK` |
| `@InlineComment("Секунды")` | Поле / компонент record | Однострочный комментарий после значения; `""` удаляет его |
| `@Header({"Первая строка", "Вторая строка"})` | Корневой класс / record | Заголовок файла; приоритетнее корневого `@Comment` |
| `@Footer("Конец файла")` | Корневой класс / record | Комментарий в конце файла |
| `@YamlIndent(4)` | Корневой класс / record | Размер отступа вложенности: `2..9` пробелов |
| `@LineWidth(120)` | Корневой класс / record | Предпочтительная ширина переноса: от `20`; `0` отключает перенос по ширине |
| `@LineBreak(Newline.CRLF)` | Корневой класс / record | Переводы строк `LF` или `CRLF` |
| `@DocumentMarkers(start = true, end = true)` | Корневой класс / record | Маркеры документа `---` и `...`; по умолчанию только начальный |

### Имена ключей

| `Format` | Результат для `maxPlayers` |
| --- | --- |
| `IDENTITY` | `maxPlayers` |
| `KEBAB_CASE` | `max-players` |
| `SNAKE_CASE` | `max_players` |
| `SCREAMING_SNAKE_CASE` | `MAX_PLAYERS` |
| `CAMEL_CASE` | `maxPlayers` |
| `PASCAL_CASE` | `MaxPlayers` |
| `DOT_CASE` | `max.players` — буквальная точка, не путь |
| `LOWER_CASE` | `maxplayers` |
| `UPPER_CASE` | `MAXPLAYERS` |

Преобразование учитывает аббревиатуры: `HTTPServerURL` → `http-server-url`.
Формат корневого класса служит общим значением для всех моделей, включая объекты внутри списков и словарей.
`@KeysFormat` на вложенном типе переопределяет формат полей именно этого типа, включая наследуемые поля;
для других вложенных типов по-прежнему действует общий формат корня или их собственная аннотация.
Аннотации классов наследуются подклассами. `@Key("My.Exact-Key")` всегда приоритетнее формата.
Ключи `Map` не переименовываются. Если два поля получат одинаковый YAML-ключ, загрузка/сохранение завершится ошибкой.

**Изменение `@KeysFormat` меняет схему чтения.** Существующие ключи автоматически не мигрируют:
переименуй их в YAML или закрепи прежние имена через `@Key`, иначе отсутствующие новые ключи получат defaults.

### Приоритеты и сочетания

- `KeysSpacing`, `KeysOrder`, `StringStyle`, `ListStyle` и `MapStyle` действуют на всё поддерево.
  Сначала применяются настройки родителя, затем аннотации типа, затем аннотации поля, содержащего этот тип.
  Это работает также с элементами коллекций, records и `Optional`.
- `KeyOrder.PRESERVE` сохраняет порядок существующего файла/вставки (поведение по умолчанию);
  `DECLARATION` выводит поля в порядке модели, наследуемые раньше собственных, неизвестные ключи — после них;
  `ALPHABETICAL` и `REVERSE_ALPHABETICAL` сортируют буквальные ключи с учётом регистра.
  `@Order` имеет приоритет над выбранным порядком. Порядок аргументов конструктора record не меняется.
- `DECLARATION` для обычного `Map` сохраняет порядок вставки. Сортировка меняет только вывод YAML, а не модель в памяти.
- Между соседними полями берётся максимум из `KeysSpacing`, `SpaceAfter` предыдущего и `SpaceBefore` следующего.
  Пустые строки перед полем идут перед его блочным комментарием. Внутри `FLOW` пустые разделители не добавляются.
- `StringFormat`: `AUTO`, `SINGLE_QUOTED`, `DOUBLE_QUOTED`, `LITERAL` (`|`), `FOLDED` (`>`).
  Стиль действует только на строковые **значения**, не на ключи, числа и boolean. YAML выбирает безопасное
  представление, если запрошенный стиль невозможен, например многострочный блок внутри `FLOW`.
  Содержимое строки, включая переводы строк, сохраняется.
- `LineWidth` — предпочтительная ширина, не жёсткий лимит: длинные неделимые значения могут превышать её.
  `YamlIndent` меняет вложенность секций; маркеры списков используют стандартное размещение Bukkit/SnakeYAML.
- `@Header({})` и `@Footer({})` очищают соответствующий комментарий. Без этих аннотаций сохраняются
  комментарии файла (корневой `@Comment` по-прежнему задаёт заголовок).
  Чтобы отличить пользовательский заголовок от комментария первого ключа, отделяй его пустой строкой.
- Некорректные диапазоны настроек и многострочный `InlineComment` вызывают `ConfigException` до записи файла.
  Без новых аннотаций используется прежняя запись Bukkit; исходное произвольное оформление YAML не гарантируется.

## Свои типы

Для нестандартного представления зарегистрируй адаптер до `load()`:

```java
public record Position(int x, int z) {}

var provider = LilConfig.provider(plugin)
    .adapter(Position.class, new ConfigAdapter<Position>() {
        @Override
        public Object serialize(Position value) {
            return value.x() + "," + value.z();
        }

        @Override
        public Position deserialize(Object value) {
            String[] parts = ((String) value).split(",");
            return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    });
```

Адаптер действует для точного объявленного класса, в том числе внутри коллекций.
Его результат — строка, boolean, число, null, список или словарь со строковыми ключами.
Новые регистрации не меняют уже открытые handle. Адаптеры не используются для ключей словарей.
Для своих Bukkit `ConfigurationSerializable` зарегистрируй класс через `ConfigurationSerialization.registerClass(...)`.
Сериализация некоторых Bukkit-типов, например `ItemStack`/`Location`, требует работающего сервера;
библиотека не эмулирует сервер и не загружает миры.

## Особенности

- Операции с файлами синхронные. Планируй их с учётом размера конфигов и правил потоков Bukkit.
- Методы handle синхронизированы, но объекты, полученные через `get()`, изменяет вызывающий код.
  Не изменяй их одновременно с `save()`/`reload()` из другого потока.
- Значения `null` и пустой `Optional` в полях YAML-секций означают отсутствие ключа по правилам Bukkit.
  После следующей загрузки для такого ключа снова применяется default. Для очищаемых настроек задай
  `null`/`Optional.empty()` как default либо используй явный флаг `enabled`.
- Полиморфизм с автоматической записью имён Java-классов не используется. Для интерфейсов/абстрактных
  типов полей нужен адаптер; наследование полей конкретной модели поддерживается.
- Зарезервированы пустые ключи, ключ `==` (служебный ключ Bukkit-сериализации) и символ U+001F.
- Библиотека использует Bukkit YAML и предоставленный сервером SnakeYAML 2.2+.
  Сборка и тесты выполняются со Spigot API **1.21.8**; совместимость с другими версиями сервера отдельно не проверялась.

## Сборка и подключение

```shell
mvn clean verify
mvn install
```

После установки в локальный Maven-репозиторий добавь в **свой плагин**:

```xml
<dependency>
    <groupId>by.likebebras</groupId>
    <artifactId>LilConfig</artifactId>
    <version>1.0</version>
</dependency>
```

Включи библиотеку в JAR плагина через Maven Shade. Например, добавь в `build/plugins` плагина:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <relocations>
                    <relocation>
                        <pattern>by.likebebras.lilconfig</pattern>
                        <shadedPattern>your.plugin.libs.lilconfig</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Замени `your.plugin` на свой пакет. Bukkit API и SnakeYAML должны оставаться `provided`:
их предоставляет сервер. LilConfig — библиотека; её JAR не устанавливается отдельно в папку `plugins`
и не требует `depend` в `plugin.yml`. Артефакт пока не опубликован в удалённый Maven-репозиторий.

Бэкенд использует [`YamlConfiguration`](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/configuration/file/YamlConfiguration.html)
и стандартный контракт [`ConfigurationSerializable`](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/configuration/serialization/ConfigurationSerializable.html).
