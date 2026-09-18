package cn.pickup.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ParcelParserTest {
    @Test public void sparseAddressIsNotBroadcastToDifferentStations() {
        List<Parcel> result = ParcelParser.parse("地点：东门驿站\n地址：东门1号\n取件码：1-2-003\n地点：西门驿站\n取件码：3-4-005", "测试");
        assertEquals(2, result.size());
        assertEquals("东门驿站", result.get(0).station);
        assertEquals("西门驿站", result.get(1).station);
        assertEquals("", result.get(1).address);
        assertTrue(result.get(1).warning.contains("对应关系"));
    }

    @Test public void mixedLabeledAndNarrativeCodesAreNotDropped() {
        List<Parcel> result = ParcelParser.parse("地点：东门驿站\n取件码：1-2-003\n凭A-03-004取件", "测试");
        assertEquals(2, result.size());
        assertEquals("A-03-004", result.get(1).code);
        assertEquals("东门驿站", result.get(1).station);
    }

    @Test public void aCodeOnTheLineAfterItsLabelIsNotCountedTwice() {
        List<Parcel> result = ParcelParser.parse("地点：东门驿站\n取件码：\n1-2-003", "测试");
        assertEquals(1, result.size());
    }

    @Test public void copiedHistoryHasTheCorrectStatus() {
        List<Parcel> result = ParcelParser.parse("地点：东门驿站\n取件码：123456", "测试");
        result.get(0).pickedAt = 1000;
        assertTrue(Parcel.summary(result, "已取").startsWith("已取 1 件"));
        assertTrue(Parcel.summary(result, "已取").contains("☑ 123456"));
    }

    @Test public void shelfCodeStationAddressAndCarrierStayTogether() {
        Parcel p = ParcelParser.parse("【中通快递】取件码：2-103-4567\n取件点：幸福小区东门菜鸟驿站\n地址：幸福路18号", "测试").get(0);
        assertEquals("2-103-4567", p.code);
        assertEquals("幸福小区东门菜鸟驿站", p.station);
        assertEquals("幸福路18号", p.address);
        assertEquals("中通", p.courier);
        assertTrue(p.needsReview);
    }

    @Test public void fullWidthLettersAndDashesAreNormalizedWithoutLosingLeadingZeros() {
        Parcel p = ParcelParser.parse("取件码：Ａ－０２－００３８\n地点：兔喜生活超市（南门店）", "测试").get(0);
        assertEquals("A-02-0038", p.code);
        assertEquals("兔喜生活超市(南门店)", p.station);
    }

    @Test public void severalCodesAtOneStationMakeSeveralRecords() {
        List<Parcel> result = ParcelParser.parse("取件点：菜鸟驿站（北门店）\n取件码：1-2-003、A-3-128、836204", "测试");
        assertEquals(3, result.size());
        assertEquals("1-2-003", result.get(0).code);
        assertEquals("A-3-128", result.get(1).code);
        assertEquals("836204", result.get(2).code);
        assertEquals(1, Parcel.groups(result).size());
    }

    @Test public void differentSmsNoticesKeepTheirOwnLocations() {
        List<Parcel> result = ParcelParser.parse("【中通快递】取件码：1-2-003，取件点：东门菜鸟驿站\n"
                + "【韵达快递】取件码：A-3-128，取件点：南门兔喜生活超市", "测试");
        assertEquals(2, result.size());
        assertEquals("东门菜鸟驿站", result.get(0).station);
        assertEquals("南门兔喜生活超市", result.get(1).station);
        assertEquals(2, Parcel.groups(result).size());
    }

    @Test public void unlabeledNarrativeStationCanBeExtracted() {
        Parcel p = ParcelParser.parse("您的快递已送达幸福小区菜鸟驿站，凭2-103-4567取件。", "测试").get(0);
        assertEquals("2-103-4567", p.code);
        assertEquals("幸福小区菜鸟驿站", p.station);
    }

    @Test public void ambiguousMultiStationTextDoesNotInventCorrespondence() {
        List<Parcel> result = ParcelParser.parse("地点：东门菜鸟驿站\n地点：西门驿站\n取件码：1-2-003\n取件码：3-4-005", "测试");
        assertEquals(2, result.size());
        for (Parcel p : result) {
            assertEquals("", p.station);
            assertTrue(p.warning.contains("多个地点"));
            assertTrue(p.original.contains("西门驿站"));
        }
    }

    @Test public void repeatedCardsPairCodesAndLocationsInBothOrders() {
        for (String text : new String[]{
                "地点：东门菜鸟驿站\n取件码：1-2-003\n地点：西门驿站\n取件码：3-4-005",
                "取件码：1-2-003\n地点：东门菜鸟驿站\n取件码：3-4-005\n地点：西门驿站"}) {
            List<Parcel> result = ParcelParser.parse(text, "测试");
            assertEquals(2, result.size());
            assertEquals("东门菜鸟驿站", result.get(0).station);
            assertEquals("西门驿站", result.get(1).station);
        }
    }

    @Test public void repeatedCodeWithinOneNoticeHasDistinctImportIdentity() {
        List<Parcel> result = ParcelParser.parse("地点：东门\n取件码：1-2-003、1-2-003", "测试");
        assertEquals(2, result.size());
        assertFalse(result.get(0).exactImportOf(result.get(1)));
    }

    @Test public void phoneTrackingDateAndOtpAreNotPickupCodes() {
        for (String text : new String[]{"联系电话13800138000，运单号1234567890123", "2026-09-17", "验证码123456，请勿泄露"}) {
            Parcel p = ParcelParser.parse(text, "测试").get(0);
            assertEquals("", p.code);
            assertTrue(p.needsReview);
        }
    }

    @Test public void labeledLongNumberIsNotTruncatedIntoAFalseCode() {
        assertEquals("", ParcelParser.parse("取件码：13800138000", "测试").get(0).code);
    }

    @Test public void missingCodeOrLocationIsPreservedForReview() {
        Parcel noCode = ParcelParser.parse("取件点：校园东门驿站，您的快递已到站", "测试").get(0);
        assertTrue(noCode.warning.contains("取件码"));
        assertTrue(noCode.original.contains("已到站"));
        Parcel noLocation = ParcelParser.parse("取件码：A-103-004", "测试").get(0);
        assertEquals("", noLocation.station);
        assertEquals("A-103-004", noLocation.code);
    }

    @Test public void sameCodeFromDifferentNoticesMustNotDisappear() {
        List<Parcel> result = ParcelParser.parse("地点：东门\n取件码：123456\n\n地点：西门\n取件码：123456", "测试");
        assertEquals(2, result.size());
        assertEquals(2, Parcel.groups(result).size());
    }

    @Test public void duplicateRulesKeepDifferentBranchesAndReusedCompletedCodes() {
        Parcel a = ParcelParser.parse("取件码：2-3-123\n地点：菜鸟驿站\n地址：东门1号", "截图1").get(0);
        Parcel b = ParcelParser.parse(a.original, "截图2").get(0);
        assertTrue(a.exactImportOf(b));
        assertTrue(a.possibleDuplicateOf(b));
        b.address = "西门2号";
        assertFalse(a.possibleDuplicateOf(b));
        assertEquals(2, Parcel.groups(Arrays.asList(a, b)).size());
        a.pickedAt = System.currentTimeMillis();
        b.address = a.address;
        assertFalse(a.exactImportOf(b));
        assertFalse(a.possibleDuplicateOf(b));
    }

    @Test public void summaryContainsEveryCodeAndGroupsByFullLocation() {
        List<Parcel> parcels = ParcelParser.parse("地点：东门驿站\n取件码：1-2-003、A-3-128\n\n地点：丰巢(南门)\n取件码：083620", "测试");
        String summary = Parcel.summary(parcels);
        assertTrue(summary.contains("待取 3 件 · 2 个已知取件点"));
        assertTrue(summary.contains("东门驿站（2 件）"));
        assertTrue(summary.contains("1-2-003"));
        assertTrue(summary.contains("A-3-128"));
        assertTrue(summary.contains("083620"));
    }
}
