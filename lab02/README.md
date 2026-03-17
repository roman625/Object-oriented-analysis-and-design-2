# Лабораторная работа — Паттерн «Factory Method»
## Предметная область: Система выбора транспорта

###  Описание предметной области
Приложение **Transport Selector** помогает пользователю подобрать оптимальный вид транспорта на основе критериев доставки:

| Критерий | Описание | Пример значений |
|----------|----------|----------------|
| **Вес груза** | Максимальная масса (кг) | 5 (дрон), 50 (велосипед), 1500 (авто) |
| **Расстояние** | Дистанция доставки (км) | 10 (дрон), 20 (велосипед), 500 (авто)  |
| **Пассажиры** | Количество людей для перевозки (кол-во) | 0 (дрон), 1 (велосипед), 5 (авто) |

**Доступные виды транспорта:**

| Транспорт | Макс. вес | Макс. расстояние | Пассажиры | Уникальные свойства |
|-----------|-----------|-----------------|-----------|-------------------|
|  Велосипед | 50 кг | 20 км | ≤ 1 | Скорость: 15 км/ч |
|  Автомобиль | 1500 кг |500 км | ≤ 5 | Мощность: 150 л.с. |
|  Дрон | 5 кг | 10 км | = 0 | Полёт до 500 м |

**Важно**: Нельзя комбинировать несовместимые параметры (например, дрон не перевозит пассажиров).


###  Описание проблемы (без паттерна)

В реализации без паттерна класс `TransportSelector` напрямую создаёт объекты через множественные `if-else` и дублирует логику проверки критериев:

```csharp
//  Плохой пример: высокая связность и дублирование
public Transport GetTransport(int weight, int distance, int passengers)
{
    Transport transport = null;
    
    // Логика выбора дрона
    if (weight <= 5 && distance <= 10 && passengers == 0)
    {
        transport = new Drone(); // Прямая зависимость!
        transport.MaxWeight = 10;
        transport.CountWheel = 0;
        // ... инициализация свойств
    }
    // Логика выбора велосипеда — повторение структуры
    else if (weight <= 50 && distance <= 20 && passengers <= 1)
    {
        transport = new Bicycle(); // Прямая зависимость!
        // ...
    }
    // Логика выбора автомобиля — ещё один блок
    else if (weight <= 1500 && passengers <= 5)
    {
        transport = new Car(); // Прямая зависимость!
        // ...
    }
    
    return transport;
}
```

**Проблемы такого подхода:**

| Проблема | Последствия |
|----------|-------------|
|  **Нарушение Open/Closed Principle** | При добавлении нового транспорта (например, «Грузовик») придётся менять метод `GetTransport`, добавляя новую ветку `else if` |
|  **Высокая связность** | `TransportSelector` знает о всех конкретных классах `Bicycle`, `Car`, `Drone` и их конструкторах |
|  **Дублирование логики** | Проверки критериев (`weight <= X`, `passengers <= Y`) повторяются в нескольких местах |
|  **Сложность тестирования** | Невозможно подменить создание транспорта моком без изменения кода |

---

###  Решение: применение паттерна «Factory Method»

#### 1. Продукты: абстрактный класс и конкретные реализации

Абстрактный класс `Transport` задаёт общий контракт для всех видов транспорта:

```csharp
public abstract class Transport
{
    // Общие свойства (инкапсулированы для наследников)
    public int MaxWeight { get; protected set; }
    public int CountWheel { get; protected set; }
    public int PassengerPlace { get; protected set; }

    // Абстрактные методы — реализуются в наследниках
    public abstract string Move();
    public abstract string GetInfo();
    public abstract bool CanHandle(int weight, int distance, int passengers);
}
```

Конкретные продукты реализуют свою логику:

```csharp
public class Bicycle : Transport
{
    public int CountSpeed { get; private set; } // Уникальное свойство
    
    public Bicycle()
    {
        MaxWeight = 100; CountWheel = 2; PassengerPlace = 1; CountSpeed = 15;
    }
    
    public override string Move() => $"Велосипед двигается со скоростью {CountSpeed} км/ч";
    public override bool CanHandle(int w, int d, int p) => w <= 50 && d <= 20 && p <= 1;
}
// Аналогично: Car, Drone
```

#### 2. Абстрактный создатель (Creator)

Интерфейс фабрики через абстрактный метод:

```csharp
public abstract class Creator
{
    // Фабричный метод — объявлен, но не реализован
    protected abstract Transport CreateTransport(int weight, int distance, int passengers);
    
    // Публичный метод для клиента
    public Transport GetTransport(int weight, int distance, int passengers)
    {
        return CreateTransport(weight, distance, passengers);
    }
}
```

#### 3. Конкретные создатели (Concrete Creators)

Каждый создатель знает, как инстанцировать свой тип транспорта:

```csharp
public class BicycleCreator : Creator
{
    protected override Transport CreateTransport(int w, int d, int p)
    {
        return new Bicycle(); // Инкапсуляция создания
    }
}

public class CarCreator : Creator
{
    protected override Transport CreateTransport(int w, int d, int p)
    {
        return new Car();
    }
}

public class DroneCreator : Creator
{
    protected override Transport CreateTransport(int w, int d, int p)
    {
        return new Drone();
    }
}
```

#### 4. Основная фабрика с логикой выбора (TransportFactory)

```csharp
public class TransportFactory : Creator
{
    // Композиция: фабрика использует конкретных создателей
    private BicycleCreator _bicycleCreator = new BicycleCreator();
    private CarCreator _carCreator = new CarCreator();
    private DroneCreator _droneCreator = new DroneCreator();

    protected override Transport CreateTransport(int weight, int distance, int passengers)
    {
        // Единая точка принятия решения о типе транспорта
        if (weight <= 5 && distance <= 10 && passengers == 0)
        {
            return _droneCreator.CreateTransportPublic(weight, distance, passengers);
        }
        else if (weight <= 50 && distance <= 20 && passengers <= 1)
        {
            return _bicycleCreator.CreateTransportPublic(weight, distance, passengers);
        }
        else if (weight <= 1500 && passengers <= 5)
        {
            return _carCreator.CreateTransportPublic(weight, distance, passengers);
        }
        return null; // Нет подходящего варианта
    }
}
```

#### 5. Клиент паттерна: UserTab и Form1

Клиент работает через абстракции и не зависит от конкретных классов:

```csharp
public class UserTab
{
    private Transport _currentTransport;
    private Creator _creator; // Зависимость от абстракции!

    public UserTab()
    {
        _creator = new TransportFactory(); // Единственное место создания фабрики
    }

    public Transport GetSelectedTransport(int weight, int distance, int passengers)
    {
        // Запрос через интерфейс фабрики
        _currentTransport = _creator.GetTransport(weight, distance, passengers);
        UpdateProperties();
        return _currentTransport;
    }
}
```

GUI (`Form1`) вызывает бизнес-логику и отображает результат:

```csharp
private void btnFind_Click(object sender, EventArgs e)
{
    // Получение данных из UI
    int weight = (int)nudWeight.Value;
    int distance = (int)nudDistance.Value;
    int passengers = (int)nudPassengers.Value;

    // Делегирование бизнес-логике
    var transport = _userTab.GetSelectedTransport(weight, distance, passengers);
    
    // Отображение результата (полиморфизм)
    if (transport != null)
    {
        lblInfo.Text = transport.GetInfo(); // Вызов переопределённого метода
        lblMove.Text = "> " + transport.Move();
    }
}
```

---

###  Диаграмма классов (UML)

<img width="1700" height="762" alt="image" src="https://github.com/user-attachments/assets/28bf2a1d-564e-4478-b988-e0293a7c2af1" />


###  Запуск приложения

**Требования:**
- .NET Framework 4.7.2+ или .NET 6+
- Visual Studio 2019+ или Rider

**Инструкции:**

1. Откройте решение `Mya_no_pat.sln` в Visual Studio
2. Убедитесь, что проект `Mya_no_pat` установлен как стартовый
3. Нажмите `F5` или кнопку  **Start**

**Альтернатива через CLI:**
```bash
cd Mya_no_pat
dotnet run
```

**Использование:**
1. Введите параметры доставки:
   - Вес груза (кг): `10`
   - Расстояние (км): `5`
   - Пассажиры: `0`
2. Нажмите **«Найти подходящий транспорт»**
3. Просмотрите результат с изображением и характеристиками

---

###  Вывод: преимущества применения Factory Method

| Преимущество | Как реализовано в коде |
|--------------|----------------------|
|  **Соблюдение Open/Closed Principle** | Для добавления нового транспорта (например, `Scooter`) достаточно создать класс `Scooter : Transport` и `ScooterCreator : Creator`. Код `TransportFactory` и `UserTab` изменять не нужно |
|  **Низкая связность** | `UserTab` зависит только от абстракции `Creator`, а не от конкретных классов транспорта. `Form1` работает только с интерфейсом `Transport` |
|  **Инкапсуляция логики создания** | Каждый `Creator` знает, как правильно инициализировать свой транспорт. Логика выбора вынесена в `TransportFactory` |
|  **Устранение дублирования** | Проверки критериев (`weight <= X`) находятся в одном месте — методе `CreateTransport` фабрики |
|  **Полиморфизм в UI** | `Form1` вызывает `transport.GetInfo()` и `transport.Move()` без знания конкретного типа — код чище и расширяемее |
|  **Тестируемость** | Легко подменить `TransportFactory` на мок-фабрику для юнит-тестов `UserTab` |

  **Когда использовать Factory Method?**  
 Когда класс не может заранее знать, какие объекты ему нужно создавать, или когда нужно предоставить пользователю/подклассу возможность выбирать тип создаваемого объекта. Идеально подходит для иерархий продуктов с общей абстракцией.

---

# Сравнительная таблица порождающих паттернов проектирования

| Критерий | Factory Method | Abstract Factory | Singleton | Builder | Prototype |
|----------|---------------|------------------|-----------|---------|-----------|
| **Назначение** | Делегирует создание объектов подклассам | Создаёт семейства связанных объектов | Гарантирует единственный экземпляр класса | Пошаговое создание сложных объектов | Клонирование существующих объектов |
| **Проблема** | Класс не знает, какие объекты создавать | Нужно создать совместимые объекты без привязки к платформе | Нужно контролировать доступ к общему ресурсу | Сложный объект с множеством параметров | Создание нового объекта дорого (БД, сеть) |
| **Решение** | Подкласс решает, какой класс инстанцировать | Фабрика создаёт целое семейство продуктов | Приватный конструктор + статический метод | Отдельный класс строит объект пошагово | Клонирование вместо new |
| **Структура** | Наследование + полиморфизм | Композиция + множественные фабрики | Статическое поле + приватный конструктор | Director + Builder + Product | Interface Clone + ConcretePrototype |
| **Преимущества** | Простота, расширяемость, принцип OCP | Гарантированная совместимость, единая точка создания, принцип DIP | Контроль доступа, глобальная точка доступа, ленивая инициализация | Читаемый код, иммутабельность, пошаговая сборка | Производительность, избегание повторов, глубокое/поверхностное копирование |
| **Недостатки** | Усложнение кода, много подклассов | Усложнение архитектуры, много интерфейсов | Нарушает SRP, сложность тестирования, global state | Много классов, избыточность для простых объектов | Сложность клонирования, циклические ссылки, глубокое vs поверхностное |
| **Связность** | Низкая (зависит от абстракции) | Очень низкая (полная абстракция) | Высокая (глобальный доступ) | Низкая (разделение ответственности) | Низкая (клонирование без знания структуры) |
| **Производительность** | Стандартная | Небольшие накладные расходы | Быстрый доступ | Медленная сборка | Быстрое клонирование |
| **Тестируемость** | Хорошая (можно замокать) | Отличная (зависимость от интерфейса) | Плохая (глобальное состояние) | Хорошая (изоляция) | Средняя (сложность сравнения) |
| **Когда использовать** | Расширение через наследование, неизвестен тип заранее | Семейства продуктов, зависимость от платформы | Логирование, кэширование, пулы соединений | Сложные объекты, разные представления, конструктор с 5+ параметрами | Дорогое создание, снимки состояния (Memento), кэширование прототипов |
| **Когда НЕ использовать** | Простые объекты, нет иерархии | Один продукт, простая логика | Многопоточность без синхронизации, частое создание/удаление | Простые объекты, все параметры обязательны | Объекты с мутациями, сложные графы объектов |
| **Ключевые слова** | Наследование, подкласс, полиморфизм | Семейство, платформа, кроссплатформенность | Единственный, глобальный, статический | Пошагово, директор, fluent API | Клон, клонирование, копирование |
| **Гибкость** | Высокая (новые подклассы) | Очень высокая (новые семейства) | Низкая (один экземпляр) | Высокая (разные билдеры) | Средняя (зависит от клонирования) |








