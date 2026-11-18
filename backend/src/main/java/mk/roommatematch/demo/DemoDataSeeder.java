package mk.roommatematch.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.auth.AuthDtos.RegisterRequest;
import mk.roommatematch.auth.AuthService;
import mk.roommatematch.auth.UserRepository;
import mk.roommatematch.listing.Amenity;
import mk.roommatematch.listing.ListingDtos.ListingRequest;
import mk.roommatematch.listing.ListingDtos.ListingResponse;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.listing.ListingService;
import mk.roommatematch.profile.ProfileDtos.ProfileRequest;
import mk.roommatematch.profile.ProfileEnums.CleaningStyle;
import mk.roommatematch.profile.ProfileEnums.CommunicationLevel;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.PartnerStays;
import mk.roommatematch.profile.ProfileEnums.PartyAttitude;
import mk.roommatematch.profile.ProfileEnums.SharingStyle;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.SoundLevel;
import mk.roommatematch.profile.ProfileService;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import mk.roommatematch.reference.DormRoomOption;
import mk.roommatematch.reference.HobbyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fills an empty database with made-up students for a demonstration. It runs only with the
 * "demo" profile, goes through the same services as real users, and adds nothing the second time.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "demo12345";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String EMAIL_DOMAIN = "@students.test";
    private static final String GOCE = "ДСД Гоце Делчев";
    private static final String STIV = "Стив Наумов";
    private static final String KITCHEN = "Со кујна (блокови А и Б)";
    private static final String TOILET = "Со тоалет (блокови В и Г)";
    private static final Set<PropertyType> EITHER = Set.of(PropertyType.APARTMENT, PropertyType.HOUSE);

    private record Habits(boolean smoker, boolean pets, GuestsFrequency guests, CleaningStyle cleaning,
                          SleepSchedule sleep, SoundLevel sound, CommunicationLevel communication, SharingStyle sharing,
                          PartyAttitude parties, PartnerStays partnerStays, boolean acceptsSmoker, boolean acceptsPets,
                          GenderPreference preference) {
    }

    private static final Habits QUIET = new Habits(false, false, GuestsFrequency.RARELY, CleaningStyle.REGULAR,
            SleepSchedule.EARLY, SoundLevel.HEADPHONES, CommunicationLevel.MINIMAL, SharingStyle.SEPARATE,
            PartyAttitude.NONE, PartnerStays.NO, false, false, GenderPreference.SAME);
    private static final Habits TIDY = new Habits(false, false, GuestsFrequency.RARELY, CleaningStyle.REGULAR,
            SleepSchedule.FLEXIBLE, SoundLevel.QUIET_SPEAKER, CommunicationLevel.OCCASIONAL, SharingStyle.BASICS,
            PartyAttitude.SMALL, PartnerStays.SOMETIMES, false, true, GenderPreference.SAME);
    private static final Habits EASYGOING = new Habits(false, true, GuestsFrequency.SOMETIMES, CleaningStyle.SCHEDULE,
            SleepSchedule.FLEXIBLE, SoundLevel.QUIET_SPEAKER, CommunicationLevel.TOGETHER, SharingStyle.BASICS,
            PartyAttitude.SMALL, PartnerStays.SOMETIMES, true, true, GenderPreference.ANY);
    private static final Habits SOCIAL = new Habits(true, false, GuestsFrequency.OFTEN, CleaningStyle.WHEN_POSSIBLE,
            SleepSchedule.LATE, SoundLevel.LOUD, CommunicationLevel.TOGETHER, SharingStyle.EVERYTHING,
            PartyAttitude.PARTIES, PartnerStays.YES, true, true, GenderPreference.ANY);

    /** What a student is looking for, apart from who they are. */
    private record Wanted(Situation situation, int min, int max, Set<Area> areas, Set<PropertyType> types) {
    }

    private record Person(String key, String name, Gender gender, int birthYear, String faculty, String bio,
                          Habits habits, List<String> hobbies) {
    }

    private final AuthService auth;
    private final UserRepository users;
    private final ProfileService profiles;
    private final ListingService listings;
    private final DormRepository dorms;
    private final HobbyRepository hobbies;

    public DemoDataSeeder(AuthService auth, UserRepository users, ProfileService profiles, ListingService listings,
                          DormRepository dorms, HobbyRepository hobbies) {
        this.auth = auth;
        this.users = users;
        this.profiles = profiles;
        this.listings = listings;
        this.dorms = dorms;
        this.hobbies = hobbies;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(email("ana"))) {
            seedSkopje();
            seedOtherTowns();
            seedDorms();
            log.info("Demo data added");
        }
        // Both steps skip what is already there, so a database seeded earlier catches up as well.
        seedMoreListings();
        replaceDrawings();
        addAmenities();
    }

    /** What each demo home offers, by its photos. Houses have a yard, blocks of flats a lift. */
    private static final Map<String, Set<Amenity>> AMENITIES = Map.ofEntries(
            Map.entry("apartments/1", Set.of(Amenity.BALCONY, Amenity.WIFI, Amenity.CENTRAL_HEATING, Amenity.WASHING_MACHINE)),
            Map.entry("apartments/2", Set.of(Amenity.WIFI, Amenity.AIR_CONDITIONING, Amenity.ELEVATOR)),
            Map.entry("apartments/3", Set.of(Amenity.BALCONY, Amenity.WIFI, Amenity.WASHING_MACHINE)),
            Map.entry("apartments/4", Set.of(Amenity.PARKING, Amenity.BALCONY, Amenity.WIFI, Amenity.ELEVATOR)),
            Map.entry("apartments/5", Set.of(Amenity.WIFI, Amenity.AIR_CONDITIONING, Amenity.CENTRAL_HEATING, Amenity.DISHWASHER)),
            Map.entry("apartments/6", Set.of(Amenity.BALCONY, Amenity.WIFI, Amenity.WASHING_MACHINE, Amenity.ELEVATOR)),
            Map.entry("apartments/7", Set.of(Amenity.PARKING, Amenity.WIFI, Amenity.AIR_CONDITIONING)),
            Map.entry("apartments/8", Set.of(Amenity.BALCONY, Amenity.WIFI, Amenity.WASHING_MACHINE, Amenity.DISHWASHER)),
            Map.entry("apartments/9", Set.of(Amenity.BALCONY, Amenity.WIFI, Amenity.AIR_CONDITIONING)),
            Map.entry("houses/1", Set.of(Amenity.YARD, Amenity.PARKING, Amenity.WIFI, Amenity.WASHING_MACHINE)),
            Map.entry("houses/2", Set.of(Amenity.YARD, Amenity.PARKING, Amenity.WIFI, Amenity.CENTRAL_HEATING)),
            Map.entry("houses/3", Set.of(Amenity.YARD, Amenity.PARKING, Amenity.BALCONY, Amenity.WIFI)),
            Map.entry("houses/4", Set.of(Amenity.YARD, Amenity.PARKING, Amenity.WIFI, Amenity.AIR_CONDITIONING)),
            Map.entry("houses/5", Set.of(Amenity.YARD, Amenity.WIFI, Amenity.WASHING_MACHINE)));

    /** Every demo owner with a listing, and the photos of their home. */
    private static final Map<String, String> PHOTO_SETS = Map.ofEntries(
            Map.entry("ana", "apartments/1"), Map.entry("stefan", "apartments/2"), Map.entry("mila", "apartments/3"),
            Map.entry("teodora", "apartments/4"), Map.entry("aleksandar", "apartments/5"),
            Map.entry("katerina", "apartments/6"), Map.entry("viktor", "apartments/7"),
            Map.entry("martina", "apartments/8"), Map.entry("ilija", "apartments/9"),
            Map.entry("marko", "houses/1"), Map.entry("petar", "houses/2"), Map.entry("dimitar", "houses/3"),
            Map.entry("besa", "houses/4"), Map.entry("marija", "houses/5"));

    /** Demo listings made before amenities existed have none, or only the parking they had then. */
    private void addAmenities() {
        PHOTO_SETS.forEach((key, photoSet) -> users.findByEmail(email(key)).ifPresent(user -> {
            ListingResponse l;
            try {
                l = listings.mine(user.getId());
            } catch (ResponseStatusException noListing) {
                return;
            }
            if (l.amenities().size() > 1) {
                return;
            }
            listings.update(l.id(), user.getId(), new ListingRequest(l.propertyType(), l.address(), l.area(),
                    l.latitude(), l.longitude(), l.monthlyRent(), l.freeSpots(), l.description(), AMENITIES.get(photoSet)));
        }));
    }

    /** Owners whose listings were first seeded with drawings, and the photos they get instead. */
    private static final Map<String, String> FIRST_PHOTOS = Map.of(
            "ana", "apartments/1",
            "stefan", "apartments/2",
            "mila", "apartments/3",
            "marko", "houses/1",
            "petar", "houses/2");

    /** Listings seeded before there were photos still show drawings, which are the only PNG images here. */
    private void replaceDrawings() {
        FIRST_PHOTOS.forEach((key, photoSet) -> users.findByEmail(email(key)).ifPresent(user -> {
            ListingResponse listing;
            try {
                listing = listings.mine(user.getId());
            } catch (ResponseStatusException noListing) {
                return;
            }
            boolean drawingsOnly = !listing.images().isEmpty()
                    && listing.images().stream().allMatch(image -> image.url().endsWith(".png"));
            if (drawingsOnly) {
                // A listing must keep at least one image, so the photos go in before the drawings come out.
                listings.addImages(listing.id(), user.getId(), photos(photoSet));
                listing.images().forEach(image -> listings.deleteImage(listing.id(), user.getId(), image.id()));
                log.info("Demo photos added to the listing of {}", key);
            }
        }));
    }

    /** Listings for the rest of the photos. Each owner is added only once. */
    private void seedMoreListings() {
        if (!users.existsByEmail(email("teodora"))) {
            long owner = apartment(new Person("teodora", "Теодора", Gender.FEMALE, 2003, "ФЕИТ",
                            "Студирам електротехника, сакам ред и мирни вечери.", TIDY,
                            List.of("Програмирање", "Музика", "Читање")),
                    has(Set.of(Area.KISELA_VODA), 120, 160));
            listing(owner, PropertyType.APARTMENT, Area.KISELA_VODA, 41.9800, 21.4395, 140, 1,
                    "Уреден стан во Кисела Вода, со добра врска со автобус до центарот.", "apartments/4");
        }
        if (!users.existsByEmail(email("aleksandar"))) {
            long owner = apartment(new Person("aleksandar", "Александар", Gender.MALE, 2002, "ФИНКИ",
                            "Работам како програмер со скратено време, барам цимер што учи.", EASYGOING,
                            List.of("Програмирање", "Видео игри", "Фитнес")),
                    has(Set.of(Area.CENTAR), 150, 200));
            listing(owner, PropertyType.APARTMENT, Area.CENTAR, 41.9965, 21.4230, 170, 1,
                    "Стан во центарот, пешки до плоштадот и до автобусите за кампусот.", "apartments/5");
        }
        if (!users.existsByEmail(email("katerina"))) {
            long owner = apartment(new Person("katerina", "Катерина", Gender.FEMALE, 2004, "Медицински факултет",
                            "Учам многу и барам тивка цимерка.", QUIET,
                            List.of("Читање", "Планинарење", "Готвење")),
                    has(Set.of(Area.KARPOSH), 120, 160));
            listing(owner, PropertyType.APARTMENT, Area.KARPOSH, 42.0040, 21.3950, 140, 1,
                    "Стан во Карпош, блиску до Медицинскиот факултет и клиниките.", "apartments/6");
        }
        if (!users.existsByEmail(email("viktor"))) {
            long owner = apartment(new Person("viktor", "Виктор", Gender.MALE, 2003, "Машински факултет",
                            "Сакам спорт и викенди надвор од градот.", EASYGOING,
                            List.of("Спорт", "Планинарење", "Фотографирање")),
                    has(Set.of(Area.GAZI_BABA), 100, 140));
            listing(owner, PropertyType.APARTMENT, Area.GAZI_BABA, 42.0010, 21.4690, 120, 1,
                    "Стан во Гази Баба, блиску до Природно-математичкиот и земјоделскиот факултет.", "apartments/7");
        }
        if (!users.existsByEmail(email("martina"))) {
            long owner = apartment(new Person("martina", "Мартина", Gender.FEMALE, 2004, "Правен факултет",
                            "Сакам да готвам и да имам уреден дом.", TIDY,
                            List.of("Готвење", "Танц", "Патување")),
                    has(Set.of(Area.CHAIR), 110, 150));
            listing(owner, PropertyType.APARTMENT, Area.CHAIR, 42.0130, 21.4410, 130, 2,
                    "Простран стан во Чаир со две слободни соби.", "apartments/8");
        }
        if (!users.existsByEmail(email("ilija"))) {
            long owner = apartment(new Person("ilija", "Илија", Gender.MALE, 2004, "Факултет за туризам и угостителство, Охрид",
                            "Работам лете во туризмот, преку година учам.", SOCIAL,
                            List.of("Патување", "Социјализирање", "Музика")),
                    has(Set.of(Area.OHRID), 90, 130));
            listing(owner, PropertyType.APARTMENT, Area.OHRID, 41.1150, 20.8000, 110, 1,
                    "Стан во Охрид, десетина минути пеш до факултетот и езерото.", "apartments/9");
        }
        if (!users.existsByEmail(email("dimitar"))) {
            long owner = apartment(new Person("dimitar", "Димитар", Gender.MALE, 2003, "ФИНКИ",
                            "Имам куќа со двор и барам двајца цимери.", EASYGOING,
                            List.of("Друштвени игри", "Готвење", "Програмирање")),
                    has(Set.of(Area.GAZI_BABA), 130, 180));
            listing(owner, PropertyType.HOUSE, Area.GAZI_BABA, 42.0050, 21.4800, 160, 2,
                    "Куќа со двор во Гази Баба, со паркинг и две слободни соби.", "houses/3");
        }
        if (!users.existsByEmail(email("besa"))) {
            long owner = apartment(new Person("besa", "Беса", Gender.FEMALE, 2004, "Универзитет на Југоисточна Европа",
                            "Барам цимерка за куќа блиску до универзитетот.", TIDY,
                            List.of("Читање", "Фитнес", "Готвење")),
                    has(Set.of(Area.TETOVO), 80, 120));
            listing(owner, PropertyType.HOUSE, Area.TETOVO, 42.0060, 20.9710, 100, 1,
                    "Куќа во Тетово со мирно соседство и градина.", "houses/4");
        }
        if (!users.existsByEmail(email("marija"))) {
            long owner = apartment(new Person("marija", "Марија", Gender.FEMALE, 2005, "Филолошки факултет, Штип",
                            "Сакам книги и мир дома.", QUIET,
                            List.of("Читање", "Цртање и уметност", "Патување")),
                    has(Set.of(Area.STIP), 60, 100));
            listing(owner, PropertyType.HOUSE, Area.STIP, 41.7380, 22.1890, 85, 1,
                    "Куќа во Штип, блиску до Универзитетот „Гоце Делчев“.", "houses/5");
        }
    }

    private void seedSkopje() {
        long ana = apartment(new Person("ana", "Ана", Gender.FEMALE, 2003, "ФИНКИ",
                        "Студирам информатика и често учам дома. Барам мирна цимерка.", TIDY,
                        List.of("Програмирање", "Читање", "Филмови и серии")),
                has(Set.of(Area.KARPOSH), 110, 150));
        long elena = apartment(new Person("elena", "Елена", Gender.FEMALE, 2004, "ФИНКИ",
                        "Живеам со Ана. Сакам да готвам за повеќе луѓе.", TIDY,
                        List.of("Готвење", "Читање", "Патување")),
                has(Set.of(Area.KARPOSH), 110, 150));
        String code = listing(ana, PropertyType.APARTMENT, Area.KARPOSH, 42.0052, 21.4068, 130, 1,
                "Двособен стан близу до кампусот на ФИНКИ и ФЕИТ, со балкон и нова кујна.", "apartments/1");
        listings.join(elena, code);

        long stefan = apartment(new Person("stefan", "Стефан", Gender.MALE, 2002, "ФЕИТ",
                        "Последна година, навечер работам од дома.", EASYGOING,
                        List.of("Видео игри", "Спорт", "Програмирање")),
                has(Set.of(Area.AERODROM), 130, 170));
        listing(stefan, PropertyType.APARTMENT, Area.AERODROM, 41.9849, 21.4662, 150, 1,
                "Светол стан во Аеродром, блиску до автобуска станица и маркет.", "apartments/2");

        long marko = apartment(new Person("marko", "Марко", Gender.MALE, 2003, "Економски факултет",
                        "Сакам друштво и често викам другари.", SOCIAL,
                        List.of("Социјализирање", "Музика", "Спорт")),
                has(Set.of(Area.CENTAR), 150, 200));
        listing(marko, PropertyType.HOUSE, Area.CENTAR, 41.9958, 21.4318, 180, 2,
                "Куќа со двор блиску до центарот. Има две слободни соби.", "houses/1");

        apartment(new Person("iva", "Ива", Gender.FEMALE, 2005, "Медицински факултет",
                        "Втора година медицина, ми треба тивок простор за учење.", QUIET,
                        List.of("Читање", "Планинарење", "Програмирање")),
                needs(Set.of(Area.KARPOSH, Area.CENTAR), 100, 160));
        apartment(new Person("nikola", "Никола", Gender.MALE, 2004, "ФЕИТ",
                        "Барам стан во Аеродром или Кисела Вода.", EASYGOING,
                        List.of("Видео игри", "Фотографирање", "Спорт")),
                needs(Set.of(Area.AERODROM, Area.KISELA_VODA), 120, 170));
        apartment(new Person("sara", "Сара", Gender.FEMALE, 2004, "Правен факултет",
                        "Барам цимерка за заедно да најдеме стан.", TIDY,
                        List.of("Читање", "Танц", "Патување")),
                together(Set.of(Area.CENTAR, Area.KARPOSH), 120, 200));
        apartment(new Person("tamara", "Тамара", Gender.FEMALE, 2005, "Филозофски факултет",
                        "Сакам уреден дом и мирни вечери.", TIDY,
                        List.of("Читање", "Цртање и уметност", "Танц")),
                together(Set.of(Area.CENTAR, Area.CHAIR), 100, 180));
        apartment(new Person("david", "Давид", Gender.MALE, 2003, "ФИНКИ",
                        "Барам цимер за заеднички стан во Гази Баба.", EASYGOING,
                        List.of("Програмирање", "Видео игри", "Друштвени игри")),
                together(Set.of(Area.GAZI_BABA), 100, 180));
        apartment(new Person("bojan", "Бојан", Gender.MALE, 2004, "Природно-математички факултет",
                        "Студирам математика и сакам шах.", EASYGOING,
                        List.of("Друштвени игри", "Читање", "Спорт")),
                together(Set.of(Area.GAZI_BABA, Area.AERODROM), 90, 160));
    }

    private void seedOtherTowns() {
        long petar = apartment(new Person("petar", "Петар", Gender.MALE, 2003, "Технички факултет, Битола",
                        "Имам куќа блиску до факултетот.", EASYGOING, List.of("Планинарење", "Спорт", "Готвење")),
                has(Set.of(Area.BITOLA), 70, 110));
        listing(petar, PropertyType.HOUSE, Area.BITOLA, 41.0270, 21.3350, 90, 1,
                "Мирна куќа со градина, десет минути пеш до Техничкиот факултет.", "houses/2");
        apartment(new Person("goran", "Горан", Gender.MALE, 2005, "Технички факултет, Битола",
                        "Прва година, барам сместување во Битола.", EASYGOING, List.of("Спорт", "Музика", "Видео игри")),
                needs(Set.of(Area.BITOLA), 60, 110));

        long mila = apartment(new Person("mila", "Мила", Gender.FEMALE, 2004, "Економски факултет, Штип",
                        "Имам стан во центарот на Штип.", TIDY, List.of("Танц", "Патување", "Готвење")),
                has(Set.of(Area.STIP), 60, 100));
        listing(mila, PropertyType.APARTMENT, Area.STIP, 41.7425, 22.1965, 80, 1,
                "Стан во центарот на Штип, блиску до универзитетот.", "apartments/3");
        apartment(new Person("kristina", "Кристина", Gender.FEMALE, 2005, "Филолошки факултет, Штип",
                        "Студирам јазици и сакам мир дома.", TIDY, List.of("Читање", "Патување", "Фотографирање")),
                needs(Set.of(Area.STIP), 50, 100));

        apartment(new Person("arta", "Арта", Gender.FEMALE, 2004, "Универзитет на Југоисточна Европа",
                        "Барам цимерка во Тетово.", TIDY, List.of("Читање", "Готвење", "Фитнес")),
                together(Set.of(Area.TETOVO), 80, 130));
        apartment(new Person("florina", "Флорина", Gender.FEMALE, 2003, "Државен универзитет, Тетово",
                        "Сакам спорт и уреден дом.", TIDY, List.of("Фитнес", "Патување", "Готвење")),
                together(Set.of(Area.TETOVO), 70, 120));
    }

    private void seedDorms() {
        dorm(new Person("jana", "Јана", Gender.FEMALE, 2005, "ФИНКИ", "Сакам програмирање и мир во собата.", QUIET,
                List.of("Програмирање", "Читање", "Филмови и серии")), GOCE, KITCHEN);
        dorm(new Person("simona", "Симона", Gender.FEMALE, 2004, "ФЕИТ", "Често готвам за цимерките.", TIDY,
                List.of("Готвење", "Програмирање", "Музика")), GOCE, KITCHEN);
        dorm(new Person("vera", "Вера", Gender.FEMALE, 2005, "ФИНКИ", "Барам цимерка со слични навики.", QUIET,
                List.of("Читање", "Програмирање", "Цртање и уметност")), GOCE, TOILET);
        dorm(new Person("filip", "Филип", Gender.MALE, 2004, "Машински факултет", "Сакам кошарка.", EASYGOING,
                List.of("Спорт", "Видео игри", "Музика")), GOCE, TOILET);
        dorm(new Person("luka", "Лука", Gender.MALE, 2005, "ФЕИТ", "Прва година, барам цимер.", EASYGOING,
                List.of("Видео игри", "Спорт", "Програмирање")), GOCE, KITCHEN);
        dorm(new Person("riste", "Ристе", Gender.MALE, 2004, "Факултет за земјоделски науки и храна", "Живеам во бараките.",
                EASYGOING, List.of("Планинарење", "Спорт", "Готвење")), STIV, "Бараки");
        dorm(new Person("darko", "Дарко", Gender.MALE, 2003, "Природно-математички факултет", "Живеам во зградите.",
                EASYGOING, List.of("Читање", "Спорт", "Друштвени игри")), STIV, "Згради");
        dorm(new Person("lea", "Леа", Gender.FEMALE, 2005, "Технички факултет, Битола", "Барам цимерка во Битола.", TIDY,
                List.of("Танц", "Музика", "Читање")), "ДСД Кочо Рацин", null);
        dorm(new Person("nina", "Нина", Gender.FEMALE, 2004, "Технички факултет, Битола", "Сакам музика и филмови.", TIDY,
                List.of("Музика", "Филмови и серии", "Читање")), "ДСД Кочо Рацин", null);
        dorm(new Person("hana", "Хана", Gender.FEMALE, 2005, "Економски факултет, Штип", "Учам навечер.", TIDY,
                List.of("Читање", "Фитнес", "Патување")), "ПСД Логос", null);
        dorm(new Person("ema", "Ема", Gender.FEMALE, 2004, "Филолошки факултет, Штип", "Сакам мирна соба.", TIDY,
                List.of("Читање", "Патување", "Цртање и уметност")), "ПСД Логос", null);
        dorm(new Person("angela", "Ангела", Gender.FEMALE, 2005, "Машински факултет, Штип", "Барам цимерка.", TIDY,
                List.of("Фитнес", "Музика", "Танц")), "ПСД Хилјадников", null);
    }

    private static Wanted has(Set<Area> areas, int min, int max) {
        return new Wanted(Situation.HAS_PLACE, min, max, areas, EITHER);
    }

    private static Wanted needs(Set<Area> areas, int min, int max) {
        return new Wanted(Situation.NEEDS_PLACE, min, max, areas, EITHER);
    }

    private static Wanted together(Set<Area> areas, int min, int max) {
        return new Wanted(Situation.SEARCH_TOGETHER, min, max, areas, EITHER);
    }

    private long apartment(Person person, Wanted wanted) {
        return save(person, HousingType.APARTMENT, wanted, null, null);
    }

    private void dorm(Person person, String dormName, String roomOption) {
        Dorm dorm = dorms.findAllWithRoomOptions().stream()
                .filter(candidate -> candidate.getName().equals(dormName))
                .findFirst()
                .orElseThrow();
        Long optionId = dorm.getRoomOptions().stream()
                .filter(option -> option.getName().equals(roomOption))
                .map(DormRoomOption::getId)
                .findFirst()
                .orElse(null);
        save(person, HousingType.DORM, null, dorm.getId(), optionId);
    }

    private long save(Person person, HousingType housing, Wanted wanted, Long dormId, Long roomOptionId) {
        auth.registerConfirmed(new RegisterRequest(email(person.key()), PASSWORD));
        long userId = users.findByEmail(email(person.key())).orElseThrow().getId();
        Map<String, Long> hobbyIds = hobbies.findAll().stream()
                .collect(Collectors.toMap(hobby -> hobby.getName(), hobby -> hobby.getId()));
        Habits habits = person.habits();
        profiles.save(userId, new ProfileRequest(
                person.name(),
                person.gender(),
                LocalDate.of(person.birthYear(), 3, 15),
                person.faculty(),
                person.bio(),
                housing,
                wanted == null ? null : wanted.situation(),
                dormId,
                roomOptionId,
                wanted == null ? null : wanted.min(),
                wanted == null ? null : wanted.max(),
                wanted == null ? Set.of() : wanted.areas(),
                wanted == null ? Set.of() : wanted.types(),
                habits.smoker(),
                habits.pets(),
                habits.guests(),
                habits.cleaning(),
                habits.sleep(),
                habits.sound(),
                habits.communication(),
                habits.sharing(),
                habits.parties(),
                habits.partnerStays(),
                habits.acceptsSmoker(),
                habits.acceptsPets(),
                habits.preference(),
                person.hobbies().stream().map(hobbyIds::get).collect(Collectors.toSet())));
        return userId;
    }

    private String listing(long owner, PropertyType type, Area area, double latitude, double longitude, int rent,
                           int spots, String description, String photoSet) {
        ListingResponse created = listings.create(owner, new ListingRequest(type, "ул. Пример бр. " + (owner % 40 + 1), area,
                latitude, longitude, rent, spots, description, AMENITIES.get(photoSet)));
        listings.addImages(created.id(), owner, photos(photoSet));
        return created.inviteCode();
    }

    /** The photos of one demo home, in the order of their numbered file names. */
    private static List<MultipartFile> photos(String photoSet) {
        try {
            Resource[] found = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:demo-photos/" + photoSet + "/*.jpg");
            return Arrays.stream(found)
                    .sorted(Comparator.comparingInt(DemoDataSeeder::number))
                    .map(resource -> {
                        try (InputStream in = resource.getInputStream()) {
                            return (MultipartFile) new DemoPhoto(in.readAllBytes());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int number(Resource resource) {
        String name = String.valueOf(resource.getFilename());
        return Integer.parseInt(name.substring(0, name.indexOf('.')));
    }

    private static String email(String key) {
        return "demo-" + key + EMAIL_DOMAIN;
    }

    private record DemoPhoto(byte[] content) implements MultipartFile {

        @Override
        public String getName() {
            return "images";
        }

        @Override
        public String getOriginalFilename() {
            return "demo.jpg";
        }

        @Override
        public String getContentType() {
            return "image/jpeg";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            java.nio.file.Files.write(destination.toPath(), content);
        }
    }
}
