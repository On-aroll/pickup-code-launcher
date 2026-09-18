package cn.pickup.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Conservative extraction of explicit pickup fields. Every import stays a draft. */
final class ParcelParser {
    private static final class Field {
        final String value;
        final int position;
        Field(String value, int position) { this.value = value; this.position = position; }
    }
    private static final String TOKEN = "[A-Z0-9]+(?:-[A-Z0-9]+){0,5}";
    private static final Pattern LABEL_CODE = Pattern.compile(
            "(?:取件码|取货码|提货码|领取码|自提码|取件号|提货号|货架号|货架码|取件口令)"
                    + "[\\s:：为是【\\[(]*((?:" + TOKEN + ")[\\s,，、/;；]*(?:(?:" + TOKEN + ")[\\s,，、/;；]*)*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_TOKEN = Pattern.compile("(?<![A-Z0-9-])" + TOKEN + "(?![A-Z0-9-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELF_LINE = Pattern.compile("(?m)^\\s*([A-Z0-9]+(?:-[A-Z0-9]+){1,5})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PICKUP_WITH = Pattern.compile("凭[\\s:：]*([A-Z0-9]+(?:-[A-Z0-9]+){0,5})(?=[^A-Z0-9-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATION_LABEL = Pattern.compile(
            "(?:驿站名称|取件点|取件网点|自提点|站点|门店|取件地点|地点)\\s*[:：]\\s*([^\\n，,。；;]{2,80})");
    private static final Pattern ADDRESS_LABEL = Pattern.compile(
            "(?:取件地址|详细地址|地址)\\s*[:：]\\s*([^\\n，,。；;]{2,100})");
    private static final Pattern STATION_NAME = Pattern.compile(
            "[^\\n，,。；;:：【】]{0,35}?(?:菜鸟驿站|妈妈驿站|兔喜(?:生活)?(?:驿站|快递超市)|多多驿站|熊猫快收|快递超市|快递驿站|快递站|代收点|丰巢(?:快递柜)?|速递易(?:快递柜)?)"
                    + "(?:[（(][^）)\\n]{1,40}[）)]|[\\p{IsHan}A-Za-z0-9]{1,24}(?:店|站|柜))?");
    private static final Pattern COURIER = Pattern.compile("中通|圆通|申通|韵达|顺丰|极兔|京东|邮政|EMS|德邦|百世", Pattern.CASE_INSENSITIVE);

    static List<Parcel> parse(String text, String source) {
        String normalized = Parcel.normalize(text).replace("\r", "")
                .replaceAll("(?<=[A-Za-z0-9])\\s*-\\s*(?=[A-Za-z0-9])", "-");
        List<Parcel> result = new ArrayList<>();
        // Blank lines separate user-pasted notices. OCR lines use single newlines.
        for (String block : normalized.split("\\n\\s*\\n|(?=【(?:菜鸟|中通|圆通|申通|韵达|顺丰|极兔|京东|邮政|丰巢)[^】]{0,12}】)")) {
            if (block.trim().isEmpty()) continue;
            List<Field> codes = new ArrayList<>();
            Matcher labels = LABEL_CODE.matcher(block);
            while (labels.find()) {
                Matcher tokens = CODE_TOKEN.matcher(labels.group(1));
                while (tokens.find()) addCode(codes, tokens.group(), labels.start(1) + tokens.start());
            }
            {
                Matcher shelf = SHELF_LINE.matcher(block);
                while (shelf.find()) addCode(codes, shelf.group(1), shelf.start(1));
                Matcher with = PICKUP_WITH.matcher(block + " ");
                while (with.find()) {
                    String tail = block.substring(Math.min(with.end(), block.length()));
                    if (tail.matches("(?s).{0,40}(?:取件|取货|提货).*")) addCode(codes, with.group(1), with.start(1));
                }
            }
            Collections.sort(codes, (a, b) -> Integer.compare(a.position, b.position));
            List<Field> stations = fieldValues(STATION_LABEL, block);
            if (stations.isEmpty()) {
                Matcher station = STATION_NAME.matcher(block);
                while (station.find()) {
                    String name = cleanStation(station.group());
                    if (!name.isEmpty()) stations.add(new Field(name, station.start()));
                }
            }
            List<Field> addresses = fieldValues(ADDRESS_LABEL, block);
            Set<String> couriers = new LinkedHashSet<>();
            Matcher courier = COURIER.matcher(block);
            while (courier.find()) couriers.add(courier.group());

            if (codes.isEmpty()) codes.add(new Field("", 0)); // Preserve unmatched text for manual review.
            for (int i = 0; i < codes.size(); i++) {
                Parcel parcel = new Parcel();
                parcel.code = codes.get(i).value;
                parcel.station = stations.size() == 1 && distinctValues(addresses) > 1 ? "" : fieldFor(stations, i, codes);
                parcel.address = addresses.size() == 1 && distinctValues(stations) > 1 ? "" : fieldFor(addresses, i, codes);
                parcel.courier = couriers.size() == 1 ? couriers.iterator().next() : "";
                parcel.original = block.trim();
                parcel.importKey = fingerprint(parcel.original + "\n#" + i);
                parcel.source = source;
                List<String> warnings = new ArrayList<>();
                if (parcel.code.isEmpty()) warnings.add("未找到明确取件码，请补充");
                if (parcel.station.isEmpty() && parcel.address.isEmpty()) warnings.add("取件地点待补充");
                if ((!stations.isEmpty() && parcel.station.isEmpty()) || (!addresses.isEmpty() && parcel.address.isEmpty())) {
                    warnings.add("包含多个地点且对应关系不明确，请逐件核对");
                }
                if (couriers.size() > 1) warnings.add("包含多个快递公司，请核对");
                parcel.warning = join(warnings);
                result.add(parcel);
            }
        }
        return result;
    }

    private static String fieldFor(List<Field> fields, int index, List<Field> codes) {
        if (fields.size() == 1) return fields.get(0).value;
        if (fields.size() == codes.size()) {
            boolean before = true, after = true;
            for (int i = 0; i < codes.size(); i++) {
                int f = fields.get(i).position, c = codes.get(i).position;
                before &= f < c && (i == 0 || f > codes.get(i - 1).position);
                after &= f > c && (i == codes.size() - 1 || f < codes.get(i + 1).position);
            }
            // Only pair clear alternating card fields. Separate lists of codes and places stay unassigned.
            if (before || after) return fields.get(index).value;
        }
        return "";
    }

    private static int distinctValues(List<Field> fields) {
        Set<String> values = new LinkedHashSet<>();
        for (Field field : fields) values.add(field.value);
        return values.size();
    }

    private static List<Field> fieldValues(Pattern pattern, String text) {
        List<Field> out = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            value = value.replaceFirst("\\s*(?:取件码|取货码|提货码|货架号|地址|电话)\\s*[:：].*$", "");
            if (!value.isEmpty()) out.add(new Field(value, matcher.start()));
        }
        return out;
    }

    private static String cleanStation(String value) {
        String name = value.replaceFirst("^.*(?:请到|请至|已送达|已到达|已送至|已放至|存放于|存放在|到达|前往|位于)", "");
        name = name.replaceFirst("^.*(?:取件点|地点|自提点)\\s*", "");
        name = name.replaceAll("^[\\s\\[【]+|[\\s\\]】]+$", "");
        if (name.length() > 65 || name.contains("取件码")) return "";
        return name.trim();
    }

    private static void addCode(List<Field> codes, String value, int position) {
        String code = Parcel.normalizeCode(value);
        if (code.length() < 2 || code.length() > 32 || !code.matches(".*[0-9].*")) return;
        if (code.matches("[0-9]{11,}") || code.matches("(?:19|20)[0-9]{2}-[0-9]{1,2}-[0-9]{1,2}")) return;
        for (Field existing : codes) if (existing.position == position) return;
        codes.add(new Field(code, position));
    }

    private static String fingerprint(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String join(List<String> items) {
        StringBuilder result = new StringBuilder();
        for (String item : items) {
            if (result.length() > 0) result.append('；');
            result.append(item);
        }
        return result.toString();
    }
}
