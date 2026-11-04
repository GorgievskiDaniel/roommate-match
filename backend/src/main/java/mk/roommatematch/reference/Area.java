package mk.roommatematch.reference;

/** Municipalities of the City of Skopje. */
public enum Area {
    AERODROM("Аеродром"),
    BUTEL("Бутел"),
    GAZI_BABA("Гази Баба"),
    GJORCE_PETROV("Ѓорче Петров"),
    KARPOSH("Карпош"),
    KISELA_VODA("Кисела Вода"),
    SARAJ("Сарај"),
    CENTAR("Центар"),
    CHAIR("Чаир"),
    SHUTO_ORIZARI("Шуто Оризари");

    private final String label;

    Area(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
