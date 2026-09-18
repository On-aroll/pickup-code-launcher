package cn.pickup.launcher;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Local, user-reviewable pickup record. No account or platform synchronization. */
final class Parcel {
    String id = UUID.randomUUID().toString();
    String code = "";
    String station = "";
    String address = "";
    String courier = "";
    String source = "";
    String original = "";
    String warning = "";
    String importKey = "";
    long createdAt = System.currentTimeMillis();
    long pickedAt;
    boolean needsReview = true;

    static String normalizeCode(String value) {
        return normalize(value).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[‐‑‒–—−﹣]", "-").trim();
    }

    private static String key(String value) {
        return normalize(value).replaceAll("[\\s()（）]", "").toLowerCase(Locale.ROOT);
    }

    String groupKey() {
        // A chain name alone must not merge branches with different addresses.
        return key(station) + "\u001f" + key(address);
    }

    String locationLabel() {
        return station.isEmpty() ? (address.isEmpty() ? "待补充取件地点" : address) : station;
    }

    boolean exactImportOf(Parcel other) {
        return pickedAt == 0 && !importKey.isEmpty() && importKey.equals(other.importKey);
    }

    boolean possibleDuplicateOf(Parcel other) {
        return !id.equals(other.id) && pickedAt == 0 && other.pickedAt == 0
                && !code.isEmpty() && normalizeCode(code).equals(normalizeCode(other.code))
                && (!station.isEmpty() || !address.isEmpty()) && groupKey().equals(other.groupKey());
    }

    static Map<String, List<Parcel>> groups(List<Parcel> parcels) {
        Map<String, List<Parcel>> groups = new LinkedHashMap<>();
        for (Parcel parcel : parcels) {
            String key = parcel.groupKey();
            List<Parcel> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(parcel);
        }
        return groups;
    }

    static String summary(List<Parcel> parcels) {
        return summary(parcels, "待取");
    }

    static String summary(List<Parcel> parcels, String state) {
        int locations = 0;
        for (List<Parcel> group : groups(parcels).values()) {
            Parcel first = group.get(0);
            if (!first.station.isEmpty() || !first.address.isEmpty()) locations++;
        }
        StringBuilder out = new StringBuilder(state).append(' ').append(parcels.size())
                .append(" 件 · ").append(locations).append(" 个已知取件点\n");
        for (List<Parcel> group : groups(parcels).values()) {
            Parcel first = group.get(0);
            out.append('\n').append(first.locationLabel()).append("（").append(group.size()).append(" 件）\n");
            if (!first.address.isEmpty() && !first.address.equals(first.locationLabel())) {
                out.append(first.address).append('\n');
            }
            for (Parcel parcel : group) {
                out.append(parcel.pickedAt > 0 ? "☑ " : "□ ").append(parcel.code.isEmpty() ? "待补充取件码" : parcel.code);
                if (!parcel.courier.isEmpty()) out.append(" · ").append(parcel.courier);
                out.append('\n');
            }
        }
        return out.toString().trim();
    }
}
