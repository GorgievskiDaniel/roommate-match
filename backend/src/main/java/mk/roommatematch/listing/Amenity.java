package mk.roommatematch.listing;

import mk.roommatematch.listing.ListingDtos.PropertyType;

/** What a flat or house offers. A yard belongs to a house and a lift to a block of flats. */
public enum Amenity {
    PARKING("Паркинг"),
    BALCONY("Тераса"),
    YARD("Двор"),
    WIFI("WiFi"),
    AIR_CONDITIONING("Клима"),
    CENTRAL_HEATING("Централно греење"),
    WASHING_MACHINE("Машина за перење"),
    DISHWASHER("Машина за садови"),
    ELEVATOR("Лифт");

    private final String label;

    Amenity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean fits(PropertyType type) {
        return switch (this) {
            case YARD -> type == PropertyType.HOUSE;
            case ELEVATOR -> type == PropertyType.APARTMENT;
            default -> true;
        };
    }
}
