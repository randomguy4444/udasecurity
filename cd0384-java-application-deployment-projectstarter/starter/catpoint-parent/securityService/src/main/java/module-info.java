module securityService {
    requires java.desktop;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    requires miglayout;
    requires imageService;
    requires org.junit.jupiter.api;
    opens com.udacity.security.data to com.google.gson;
}