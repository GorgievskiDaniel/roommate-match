package mk.roommatematch.reference;

/** Where someone looks for a place: a municipality of Skopje or any other town in North Macedonia. */
public enum Area {
    AERODROM("Аеродром", true, 41.98418, 21.46472),
    BUTEL("Бутел", true, 42.03289, 21.44624),
    GAZI_BABA("Гази Баба", true, 41.99844, 21.47372),
    GJORCE_PETROV("Ѓорче Петров", true, 42.00781, 21.36054),
    KARPOSH("Карпош", true, 42.00425, 21.40796),
    KISELA_VODA("Кисела Вода", true, 41.97835, 21.43993),
    SARAJ("Сарај", true, 41.99998, 21.33123),
    CENTAR("Центар", true, 41.99430, 21.43025),
    CHAIR("Чаир", true, 42.01065, 21.43832),
    SHUTO_ORIZARI("Шуто Оризари", true, 42.04055, 21.42359),

    BEROVO("Берово", false, 41.70667, 22.85356),
    BITOLA("Битола", false, 41.03160, 21.33025),
    BOGDANCI("Богданци", false, 41.20161, 22.57765),
    VALANDOVO("Валандово", false, 41.31734, 22.56198),
    VELES("Велес", false, 41.71887, 21.77057),
    VINICA("Виница", false, 41.88474, 22.50445),
    GEVGELIJA("Гевгелија", false, 41.13887, 22.50332),
    GOSTIVAR("Гостивар", false, 41.79202, 20.90817),
    DEBAR("Дебар", false, 41.52347, 20.52698),
    DELCEVO("Делчево", false, 41.96683, 22.77677),
    DEMIR_KAPIJA("Демир Капија", false, 41.40871, 22.24186),
    DEMIR_HISAR("Демир Хисар", false, 41.22399, 21.20341),
    KAVADARCI("Кавадарци", false, 41.43328, 22.01226),
    KICEVO("Кичево", false, 41.50890, 20.95852),
    KOCANI("Кочани", false, 41.92113, 22.41142),
    KRATOVO("Кратово", false, 42.07926, 22.18022),
    KRIVA_PALANKA("Крива Паланка", false, 42.20381, 22.33367),
    KRUSEVO("Крушево", false, 41.36810, 21.24813),
    KUMANOVO("Куманово", false, 42.13591, 21.71855),
    MAKEDONSKA_KAMENICA("Македонска Каменица", false, 42.02124, 22.58867),
    MAKEDONSKI_BROD("Македонски Брод", false, 41.50749, 21.21654),
    NEGOTINO("Неготино", false, 41.48442, 22.09059),
    OHRID("Охрид", false, 41.11702, 20.80177),
    PEHCEVO("Пехчево", false, 41.76357, 22.88730),
    PRILEP("Прилеп", false, 41.34450, 21.55271),
    PROBISTIP("Пробиштип", false, 41.99984, 22.17848),
    RADOVIS("Радовиш", false, 41.63983, 22.46459),
    RESEN("Ресен", false, 41.09005, 21.01306),
    SVETI_NIKOLE("Свети Николе", false, 41.86473, 21.94205),
    STRUGA("Струга", false, 41.17703, 20.67906),
    STRUMICA("Струмица", false, 41.43901, 22.63741),
    TETOVO("Тетово", false, 42.00683, 20.97286),
    STIP("Штип", false, 41.73747, 22.19359);

    private final String label;
    private final boolean skopje;
    private final double latitude;
    private final double longitude;

    Area(String label, boolean skopje, double latitude, double longitude) {
        this.label = label;
        this.skopje = skopje;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSkopje() {
        return skopje;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
