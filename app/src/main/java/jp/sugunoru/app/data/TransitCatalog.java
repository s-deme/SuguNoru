package jp.sugunoru.app.data;

import jp.sugunoru.app.model.RoutePlan;

import java.util.List;

/** Offline route identities; ordered boarding/alighting stops come from ODPT and its cache. */
public final class TransitCatalog {
    public record Service(String id, String displayName) {
        public Service {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName is required");
            }
        }
    }

    private static final List<Service> SERVICES = List.of(
            new Service("toei.to01", "都01（渋谷駅前〜新橋駅前）"),
            new Service("toei.to02", "都02（大塚駅前〜錦糸町駅前）"),
            new Service("toei.to03", "都03（四谷駅前〜晴海埠頭）"),
            new Service("toei.to04", "都04（豊海水産埠頭〜東京駅丸の内南口）"),
            new Service("toei.to05-1", "都05-1（東京ビッグサイト〜東京駅丸の内南口）"),
            new Service("toei.to06", "都06（渋谷駅前〜新橋駅前）"),
            new Service("toei.to07", "都07（錦糸町駅前〜門前仲町）"),
            new Service("toei.to08", "都08（日暮里駅前〜錦糸町駅前）"),
            new Service("toei.to10", "都10（新橋〜とうきょうスカイツリー駅前）"),
            new Service("toei.to20", "都20（錦糸町駅前〜東大島駅前）"),
            new Service("toei.to22", "都22（錦糸町駅前〜東陽町駅前）"),
            new Service("toei.to23", "都23（平井駅前〜南砂町駅前）"),
            new Service("toei.to26", "都26（亀戸駅前〜葛西駅前）"),
            new Service("toei.to40", "都40（池袋駅東口〜西新井駅前）"),
            new Service("toei.to44", "都44（北千住駅前〜駒込駅南口）"),
            new Service("toei.to58", "都58（早稲田〜上野松坂屋前）"),
            new Service("toei.to64", "都64（池袋駅東口〜江戸川橋）"),
            new Service("toei.to65", "都65（江北駅前〜日暮里駅前）"),
            new Service("toei.to75", "都75（新宿駅西口〜三宅坂）"),
            new Service("toei.to88", "都88（渋谷駅前〜目黒駅前）"),
            new Service("toei.to97", "都97（青山一丁目駅前〜品川駅高輪口）"),
            new Service("toei.ue23", "上23（平井駅前〜上野松坂屋前）"),
            new Service("toei.ue26", "上26（亀戸駅前〜上野公園）"),
            new Service("toei.kusa24", "草24（東大島駅前〜浅草寿町）"),
            new Service("toei.gyo10", "業10（新橋〜とうきょうスカイツリー駅前）"),
            new Service("toei.higashi15", "東15（東京駅八重洲口〜深川車庫前）"),
            new Service("toei.mon33", "門33（豊海水産埠頭〜亀戸駅前）"),
            new Service("toei.oji40", "王40（西新井駅前〜池袋駅東口）"),
            new Service("toei.shina91", "品91（品川駅港南口〜八潮パークタウン）"),
            new Service("toei.hashi63", "橋63（新橋〜晴海埠頭）"),
            new Service("toei.umi01", "海01（門前仲町〜東京テレポート駅前）"),
            new Service("toei.kyuko05", "急行05（錦糸町駅前〜日本科学未来館）"),
            new Service("toei.kin11", "錦11（亀戸駅前〜築地駅前）"),
            new Service("toei.kin13-kou", "錦13甲（錦糸町駅前〜晴海埠頭）"),
            new Service("toei.kin13-otsu", "錦13乙（錦糸町駅前〜深川車庫前）"),
            new Service("toei.kin18", "錦18（錦糸町駅前〜新木場駅前・国際展示場駅前）"),
            new Service("toei.kin25", "錦25（錦糸町駅前〜葛西駅前）"),
            new Service("toei.kin27", "錦27（両国駅前〜小岩駅前）"),
            new Service("toei.kin28", "錦28（錦糸町駅前〜東大島駅前）"),
            new Service("toei.kin37", "錦37（錦糸町駅前〜青戸車庫前・新四ツ木橋）"),
            new Service("toei.kin40", "錦40（錦糸町駅前〜南千住駅東口）"),
            new Service("toei.ryo28", "両28（両国駅前〜葛西橋・臨海車庫）"),
            new Service("toei.kame21", "亀21（亀戸駅前〜東陽町駅前）"),
            new Service("toei.kame23", "亀23（亀戸駅前〜江東高齢者医療センター）"),
            new Service("toei.kame24", "亀24（亀戸駅前〜葛西橋）"),
            new Service("toei.kame26", "亀26（亀戸駅前〜今井）"),
            new Service("toei.kame29", "亀29（亀戸駅前〜西葛西駅前・なぎさニュータウン）"),
            new Service("toei.sato22", "里22（亀戸駅前〜日暮里駅前）"),
            new Service("toei.higashi22", "東22（錦糸町駅前〜東京駅丸の内北口）"),
            new Service("toei.mon19", "門19（門前仲町〜深川車庫・東京ビッグサイト）"),
            new Service("toei.mon21", "門21（門前仲町〜東大島駅前）"),
            new Service("toei.ki11-kou", "木11甲（木場駅前・東陽町駅前〜新木場循環・若洲キャンプ場前）"),
            new Service("toei.you12-1", "陽12-1（東陽町駅前〜昭和大学江東豊洲病院前）"),
            new Service("toei.you12-2", "陽12-2（東陽町駅前〜豊洲駅前・豊洲市場）"),
            new Service("toei.you20", "陽20（東陽町駅前〜東大島駅前）"),
            new Service("toei.kyuko06", "急行06（森下駅前〜日本科学未来館）"),
            new Service("toei.chokko03", "直行03（錦糸町駅前→日本科学未来館）")
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

    /** Returns the ODPT route identifier for a catalog route, or an empty value when unknown. */
    static String odptBusrouteId(String routeName) {
        for (Service service : SERVICES) {
            if (!service.displayName().equals(routeName)) continue;
            String route = service.id().substring("toei.".length());
            return "odpt.Busroute:Toei."
                    + Character.toUpperCase(route.charAt(0)) + route.substring(1);
        }
        return "";
    }

}
