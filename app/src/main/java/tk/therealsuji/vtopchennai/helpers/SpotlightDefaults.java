package tk.therealsuji.vtopchennai.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tk.therealsuji.vtopchennai.models.Spotlight;

/**
 * Provides default Spotlight announcements that highlight upcoming StudentCC features.
 */
public final class SpotlightDefaults {
    private static final String EVENTS_CATEGORY = "StudentCC Events";
    private static final String CLUB_FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLScqvtvSXk4Fip-HGHzYpEALxVIHZA9trRv6zRTwC9GVR7KEcA/viewform?usp=sharing&ouid=106411055797645073276";

    private static final List<Spotlight> EVENTS_ANNOUNCEMENTS;

    static {
        List<Spotlight> announcements = new ArrayList<>();
        announcements.add(buildSpotlight(1,
                "Hey everyone! 👋 Excited to share what's coming next on StudentCC — soon, you'll be able to find all campus events in one place! 🎉",
                null));
        announcements.add(buildSpotlight(2,
                "Here's how it'll work 👇",
                null));
        announcements.add(buildSpotlight(3,
                "✅ For Clubs: Easily upload your event details (date, time, poster, etc.) and share them with the entire campus.",
                null));
        announcements.add(buildSpotlight(4,
                "✅ For Students: Explore all upcoming events in one spot – no more missing out!",
                null));
        announcements.add(buildSpotlight(5,
                "✅ For Everyone: No more scattered WhatsApp groups or endless scrolling through Instagram. Everything will be neatly organized inside StudentCC.",
                null));
        announcements.add(buildSpotlight(6,
                "👉 Club leads, you can onboard your club here: " + CLUB_FORM_URL,
                CLUB_FORM_URL));
        announcements.add(buildSpotlight(7,
                "The goal is simple – to make StudentCC the one place that connects students and clubs, giving every event the visibility it deserves.",
                null));

        EVENTS_ANNOUNCEMENTS = Collections.unmodifiableList(announcements);
    }

    private SpotlightDefaults() {
        // Utility class
    }

    public static List<Spotlight> getEventsAnnouncements() {
        List<Spotlight> copies = new ArrayList<>(EVENTS_ANNOUNCEMENTS.size());
        for (Spotlight spotlight : EVENTS_ANNOUNCEMENTS) {
            copies.add(cloneSpotlight(spotlight));
        }
        return copies;
    }

    public static int getEventsAnnouncementsCount() {
        return EVENTS_ANNOUNCEMENTS.size();
    }

    private static Spotlight buildSpotlight(int id, String announcement, String link) {
        Spotlight spotlight = new Spotlight();
        spotlight.id = id;
        spotlight.category = EVENTS_CATEGORY;
        spotlight.announcement = announcement;
        spotlight.link = link;
        spotlight.isRead = false;
        spotlight.signature = (announcement + (link == null ? "" : link)).hashCode();
        return spotlight;
    }

    private static Spotlight cloneSpotlight(Spotlight source) {
        Spotlight copy = new Spotlight();
        copy.id = source.id;
        copy.category = source.category;
        copy.announcement = source.announcement;
        copy.link = source.link;
        copy.isRead = source.isRead;
        copy.signature = source.signature;
        return copy;
    }
}
