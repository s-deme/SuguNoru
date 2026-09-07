package jp.sugunoru.app.data;

import jp.sugunoru.app.model.RoutePlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline fallback directory for the supported Toei Bus routes.
 *
 * <p>The directory deliberately contains no timetable or live-operation data. Route, stop,
 * and destination are always selected from this list. ODPT integration can replace its contents
 * after an API key and the applicable data conditions are configured.
 */
public final class TransitCatalog {
    public record Service(
            String id,
            String displayName,
            List<String> stops,
            List<String> destinations
    ) {
        public Service {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName is required");
            }
            stops = copyRequired(stops, "stops");
            destinations = copyRequired(destinations, "destinations");
        }

        private static List<String> copyRequired(List<String> values, String label) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(label + " is required");
            }
            List<String> result = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException(label + " contains blank value");
                }
                result.add(value);
            }
            return List.copyOf(result);
        }
    }

    private static final List<Service> SERVICES = List.of(
            bus("toei.to01", "都01（渋谷駅前〜新橋駅前）", "渋谷駅前", "六本木駅前", "赤坂アークヒルズ", "新橋駅前"),
            bus("toei.to02", "都02（大塚駅前〜錦糸町駅前）", "大塚駅前", "巣鴨駅前", "春日駅前", "上野広小路", "錦糸町駅前"),
            bus("toei.to03", "都03（四谷駅前〜晴海埠頭）", "四谷駅前", "四谷三丁目", "新橋駅前", "勝どき駅前", "晴海埠頭"),
            bus("toei.to04", "都04（豊海水産埠頭〜東京駅丸の内南口）", "豊海水産埠頭", "勝どき駅前", "築地", "銀座四丁目", "東京駅丸の内南口"),
            bus("toei.to05-1", "都05-1（東京ビッグサイト〜東京駅丸の内南口）", "東京ビッグサイト", "有明一丁目", "豊洲駅前", "銀座四丁目", "東京駅丸の内南口"),
            bus("toei.to06", "都06（渋谷駅前〜新橋駅前）", "渋谷駅前", "六本木駅前", "麻布十番駅前", "新橋駅前"),
            bus("toei.to07", "都07（錦糸町駅前〜門前仲町）", "錦糸町駅前", "住吉駅前", "東陽町駅前", "門前仲町"),
            bus("toei.to08", "都08（日暮里駅前〜錦糸町駅前）", "日暮里駅前", "上野駅前", "浅草一丁目", "とうきょうスカイツリー駅入口", "錦糸町駅前"),
            bus("toei.to10", "都10（新橋〜とうきょうスカイツリー駅前）", "新橋", "銀座四丁目", "日本橋", "錦糸町駅前", "とうきょうスカイツリー駅前"),
            bus("toei.to20", "都20（錦糸町駅前〜東大島駅前）", "錦糸町駅前", "住吉駅前", "西大島駅前", "東大島駅前"),
            bus("toei.to22", "都22（錦糸町駅前〜東陽町駅前）", "錦糸町駅前", "亀戸駅前", "砂町銀座", "東陽町駅前"),
            bus("toei.to23", "都23（平井駅前〜南砂町駅前）", "平井駅前", "亀戸駅前", "北砂二丁目", "南砂町駅前"),
            bus("toei.to26", "都26（亀戸駅前〜葛西駅前）", "亀戸駅前", "境川", "南砂町駅前", "葛西駅前"),
            bus("toei.to40", "都40（池袋駅東口〜西新井駅前）", "池袋駅東口", "王子駅前", "北千住駅前", "西新井駅前"),
            bus("toei.to44", "都44（北千住駅前〜駒込駅南口）", "北千住駅前", "三ノ輪駅前", "日暮里駅前", "駒込駅南口"),
            bus("toei.to58", "都58（早稲田〜上野松坂屋前）", "早稲田", "江戸川橋", "春日駅前", "上野松坂屋前"),
            bus("toei.to64", "都64（池袋駅東口〜江戸川橋）", "池袋駅東口", "東池袋四丁目", "護国寺正門前", "江戸川橋"),
            bus("toei.to65", "都65（江北駅前〜日暮里駅前）", "江北駅前", "西新井大師", "熊野前", "日暮里駅前"),
            bus("toei.to75", "都75（新宿駅西口〜三宅坂）", "新宿駅西口", "四谷駅前", "半蔵門", "三宅坂"),
            bus("toei.to88", "都88（渋谷駅前〜目黒駅前）", "渋谷駅前", "恵比寿駅前", "白金台駅前", "目黒駅前"),
            bus("toei.to97", "都97（青山一丁目駅前〜品川駅高輪口）", "青山一丁目駅前", "六本木駅前", "高輪台駅前", "品川駅高輪口"),
            bus("toei.ue23", "上23（平井駅前〜上野松坂屋前）", "平井駅前", "亀戸駅前", "浅草一丁目", "上野松坂屋前"),
            bus("toei.ue26", "上26（亀戸駅前〜上野公園）", "亀戸駅前", "錦糸町駅前", "浅草一丁目", "上野公園"),
            bus("toei.kusa24", "草24（東大島駅前〜浅草寿町）", "東大島駅前", "亀戸駅前", "錦糸町駅前", "浅草寿町"),
            bus("toei.gyo10", "業10（新橋〜とうきょうスカイツリー駅前）", "新橋", "豊洲駅前", "木場駅前", "とうきょうスカイツリー駅前"),
            bus("toei.higashi15", "東15（東京駅八重洲口〜深川車庫前）", "東京駅八重洲口", "勝どき駅前", "豊洲駅前", "深川車庫前"),
            bus("toei.mon33", "門33（豊海水産埠頭〜亀戸駅前）", "豊海水産埠頭", "門前仲町", "東陽町駅前", "亀戸駅前"),
            bus("toei.oji40", "王40（西新井駅前〜池袋駅東口）", "西新井駅前", "北千住駅前", "王子駅前", "池袋駅東口"),
            bus("toei.shina91", "品91（品川駅港南口〜八潮パークタウン）", "品川駅港南口", "天王洲アイル", "大井競馬場前", "八潮パークタウン"),
            bus("toei.hashi63", "橋63（新橋〜晴海埠頭）", "新橋", "銀座四丁目", "勝どき駅前", "晴海埠頭"),
            bus("toei.umi01", "海01（門前仲町〜東京テレポート駅前）", "門前仲町", "豊洲駅前", "有明テニスの森", "東京テレポート駅前"),
            bus("toei.kyuko05", "急行05（錦糸町駅前〜日本科学未来館）", "錦糸町駅前", "東陽町駅前", "豊洲駅前", "日本科学未来館")
    );

    private TransitCatalog() {}

    public static List<Service> services() {
        return SERVICES;
    }

    /** Returns true only for a route in this Toei Bus-only product scope. */
    public static boolean isSupported(RoutePlan plan) {
        if (plan == null || plan.mode() != RoutePlan.Mode.BUS) return false;
        for (Service service : SERVICES) {
            if (service.displayName().equals(plan.routeName())) return true;
        }
        return false;
    }

    private static Service bus(String id, String name, String... stops) {
        String first = stops[0];
        String last = stops[stops.length - 1];
        return new Service(id, name, List.of(stops), List.of(last + "方面", first + "方面"));
    }
}
