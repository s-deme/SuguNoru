package jp.sugunoru.app.data;

import jp.sugunoru.app.model.RoutePlan;

import java.util.List;

/** Offline route identities; ordered boarding/alighting stops come from ODPT and its cache. */
public final class TransitCatalog {
    public record Service(String id, String displayName, String odptBusrouteId) {
        public Service {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName is required");
            }
        }
    }

    // Verified against Toei / ODPT on 2026-09-19; see docs/TRANSIT_CATALOG.md.
    private static final List<Service> SERVICES = List.of(
            new Service("toei.to01", "都01（渋谷駅前〜新橋駅前）", "odpt.Busroute:Toei.T01"),
            new Service("toei.to02", "都02（大塚駅前〜錦糸町駅前）", "odpt.Busroute:Toei.T02"),
            new Service("toei.to03", "都03（四谷駅〜晴海五丁目ターミナル・東京駅丸の内北口）", "odpt.Busroute:Toei.T03"),
            new Service("toei.to04", "都04（豊海水産埠頭〜東京駅丸の内南口）", "odpt.Busroute:Toei.T04"),
            new Service("toei.to05-1", "都05-1（晴海埠頭〜東京駅丸の内南口）", "odpt.Busroute:Toei.T05-1"),
            new Service("toei.to06", "都06（渋谷駅前〜新橋駅前）", "odpt.Busroute:Toei.T06"),
            new Service("toei.to07", "都07（錦糸町駅前〜門前仲町）", "odpt.Busroute:Toei.To07"),
            new Service("toei.to08", "都08（日暮里駅前〜錦糸町駅前）", "odpt.Busroute:Toei.T08"),
            new Service("toei.ue23", "上23（平井駅前〜上野松坂屋前）", "odpt.Busroute:Toei.Ue23"),
            new Service("toei.ue26", "上26（亀戸駅前〜上野公園）", "odpt.Busroute:Toei.Ue26"),
            new Service("toei.kusa24", "草24（東大島駅前〜浅草寿町）", "odpt.Busroute:Toei.Kusa24"),
            new Service("toei.gyo10", "業10（新橋〜とうきょうスカイツリー駅前）", "odpt.Busroute:Toei.Nari10"),
            new Service("toei.higashi15", "東15（東京駅八重洲口〜深川車庫前）", "odpt.Busroute:Toei.Higashi15"),
            new Service("toei.mon33", "門33（豊海水産埠頭〜亀戸駅前）", "odpt.Busroute:Toei.Mon33"),
            new Service("toei.oji40", "王40甲（西新井駅前〜池袋駅東口）", "odpt.Busroute:Toei.Ou40Kou"),
            new Service("toei.shina91", "品91（品川駅港南口〜八潮パークタウン）", "odpt.Busroute:Toei.Shina91"),
            new Service("toei.hashi63", "橋63（小滝橋車庫前〜新橋駅前）", "odpt.Busroute:Toei.Hashi63"),
            new Service("toei.umi01", "海01（門前仲町〜東京テレポート駅前）", "odpt.Busroute:Toei.KM01"),
            new Service("toei.kyuko05", "急行05（錦糸町駅前〜日本科学未来館）", "odpt.Busroute:Toei.Kyuukou05"),
            new Service("toei.kin11", "錦11（亀戸駅前〜築地駅前）", "odpt.Busroute:Toei.Nishiki11"),
            new Service("toei.kin13", "錦13（錦糸町駅前〜晴海埠頭・深川車庫前）", "odpt.Busroute:Toei.Nishiki13"),
            new Service("toei.kin18", "錦18（錦糸町駅前〜新木場駅前・国際展示場駅前）", "odpt.Busroute:Toei.Nishiki18"),
            new Service("toei.kin25", "錦25（錦糸町駅前〜葛西駅前）", "odpt.Busroute:Toei.Nishiki25"),
            new Service("toei.kin27", "錦27（両国駅前〜小岩駅前）", "odpt.Busroute:Toei.Nishiki27"),
            new Service("toei.kin28", "錦28（錦糸町駅前〜東大島駅前）", "odpt.Busroute:Toei.Nishiki28"),
            new Service("toei.kin37", "錦37（錦糸町駅前〜青戸車庫前・新四ツ木橋）", "odpt.Busroute:Toei.Nishiki37"),
            new Service("toei.kin40", "錦40（錦糸町駅前〜南千住駅東口）", "odpt.Busroute:Toei.Nishiki40"),
            new Service("toei.ryo28", "両28（両国駅前〜葛西橋・臨海車庫）", "odpt.Busroute:Toei.Ryou28"),
            new Service("toei.kame21", "亀21（亀戸駅前〜東陽町駅前）", "odpt.Busroute:Toei.Kame21"),
            new Service("toei.kame23", "亀23（亀戸駅前〜江東高齢者医療センター）", "odpt.Busroute:Toei.Kame23"),
            new Service("toei.kame24", "亀24（亀戸駅前〜葛西橋）", "odpt.Busroute:Toei.Kame24"),
            new Service("toei.kame26", "亀26（亀戸駅前〜今井）", "odpt.Busroute:Toei.Kame26"),
            new Service("toei.kame29", "亀29（亀戸駅前〜西葛西駅前・なぎさニュータウン）", "odpt.Busroute:Toei.Kame29"),
            new Service("toei.sato22", "里22（亀戸駅前〜日暮里駅前）", "odpt.Busroute:Toei.Sato22"),
            new Service("toei.higashi22", "東22（錦糸町駅前〜東京駅丸の内北口）", "odpt.Busroute:Toei.Higashi22"),
            new Service("toei.mon19", "門19（門前仲町〜深川車庫・東京ビッグサイト）", "odpt.Busroute:Toei.Mon19"),
            new Service("toei.mon21", "門21（門前仲町〜東大島駅前）", "odpt.Busroute:Toei.Mon21"),
            new Service("toei.ki11-kou", "木11甲（木場駅前・東陽町駅前〜新木場循環・若洲キャンプ場前）", "odpt.Busroute:Toei.Ki11Kou"),
            new Service("toei.you12-1", "陽12-1（東陽町駅前〜昭和大学江東豊洲病院前）", "odpt.Busroute:Toei.You12-1"),
            new Service("toei.you12-2", "陽12-2（東陽町駅前〜豊洲駅前・豊洲市場）", "odpt.Busroute:Toei.You12-2"),
            new Service("toei.you20", "陽20（東陽町駅前〜東大島駅前）", "odpt.Busroute:Toei.You20"),
            new Service("toei.kyuko06", "急行06（森下駅前〜日本科学未来館）", "odpt.Busroute:Toei.Kyuukou06"),
            new Service("toei.chokko03", "直行03（錦糸町駅前→日本科学未来館）", "odpt.Busroute:Toei.Chokkou03")
    );

    private TransitCatalog() {}

    public static List<Service> services() {
        return SERVICES;
    }

    /** Keep old registrations visible/editable, but never offer withdrawn entries in the picker. */
    public static boolean isSupported(RoutePlan plan) {
        if (plan == null || plan.mode() != RoutePlan.Mode.BUS) return false;
        return !odptBusrouteId(plan.routeName()).isEmpty()
                || LEGACY_INVALID_NAMES.contains(plan.routeName());
    }

    private static final List<String> LEGACY_INVALID_NAMES = List.of(
            "都10（新橋〜とうきょうスカイツリー駅前）",
            "都20（錦糸町駅前〜東大島駅前）",
            "都22（錦糸町駅前〜東陽町駅前）",
            "都23（平井駅前〜南砂町駅前）",
            "都26（亀戸駅前〜葛西駅前）",
            "都40（池袋駅東口〜西新井駅前）",
            "都44（北千住駅前〜駒込駅南口）",
            "都58（早稲田〜上野松坂屋前）",
            "都64（池袋駅東口〜江戸川橋）",
            "都65（江北駅前〜日暮里駅前）",
            "都75（新宿駅西口〜三宅坂）",
            "都88（渋谷駅前〜目黒駅前）",
            "都97（青山一丁目駅前〜品川駅高輪口）"
    );

    /** ODPT IDs are provider data, never transliterations of our local IDs. */
    static String odptBusrouteId(String routeName) {
        String code = routeCode(routeName);
        // Old saved names refer to these same route families; stop pairs still choose the branch.
        if (code.equals("王40")) code = "王40甲";
        if (code.equals("錦13甲") || code.equals("錦13乙")) code = "錦13";
        for (Service service : SERVICES) {
            if (routeCode(service.displayName()).equals(code)) return service.odptBusrouteId();
        }
        return "";
    }

    private static String routeCode(String name) {
        return name == null ? "" : name.split("（", 2)[0];
    }
}
