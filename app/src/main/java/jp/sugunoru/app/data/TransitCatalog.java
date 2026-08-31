package jp.sugunoru.app.data;

import jp.sugunoru.app.model.RoutePlan;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, offline-first directory used to prefill common JR East and Toei Bus registrations.
 *
 * <p>It deliberately contains no timetable data and does not call an operator's private API.
 * A user can still adjust every value in the registration form and optionally attach the
 * operator's official timetable URL.
 */
public final class TransitCatalog {
    public enum Provider {
        JR_EAST("JR東日本"),
        TOEI_BUS("都営バス");

        private final String displayName;

        Provider(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Service(
            String id,
            Provider provider,
            RoutePlan.Mode mode,
            String displayName,
            List<String> stops,
            List<String> destinations
    ) {
        public Service {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
            if (provider == null || mode == null) throw new IllegalArgumentException("provider and mode are required");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName is required");
            stops = copyRequired(stops, "stops");
            destinations = copyRequired(destinations, "destinations");
        }

        private static List<String> copyRequired(List<String> values, String label) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException(label + " is required");
            }
            List<String> result = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " contains blank value");
                result.add(value);
            }
            return List.copyOf(result);
        }
    }

    private static final List<Service> SERVICES = List.of(
            train("jr-east.yamanote", "JR山手線",
                    "東京", "有楽町", "新橋", "浜松町", "田町", "高輪ゲートウェイ", "品川",
                    "大崎", "五反田", "目黒", "恵比寿", "渋谷", "原宿", "代々木", "新宿",
                    "新大久保", "高田馬場", "目白", "池袋", "大塚", "巣鴨", "駒込", "田端",
                    "西日暮里", "日暮里", "鶯谷", "上野", "御徒町", "秋葉原", "神田",
                    "品川・東京方面（外回り）", "新宿・池袋方面（内回り）"),
            train("jr-east.keihin-tohoku", "JR京浜東北・根岸線",
                    "大船", "本郷台", "港南台", "洋光台", "新杉田", "磯子", "根岸", "山手",
                    "石川町", "関内", "桜木町", "横浜", "東神奈川", "新子安", "鶴見", "川崎",
                    "蒲田", "大森", "大井町", "品川", "高輪ゲートウェイ", "田町", "浜松町",
                    "新橋", "有楽町", "東京", "神田", "秋葉原", "御徒町", "上野", "鶯谷",
                    "日暮里", "西日暮里", "田端", "上中里", "王子", "東十条", "赤羽", "川口",
                    "西川口", "蕨", "南浦和", "浦和", "北浦和", "与野", "さいたま新都心", "大宮",
                    "大船方面", "大宮方面"),
            train("jr-east.chuo-rapid", "JR中央線快速",
                    "東京", "神田", "御茶ノ水", "四ツ谷", "新宿", "中野", "高円寺", "阿佐ケ谷",
                    "荻窪", "西荻窪", "吉祥寺", "三鷹", "武蔵境", "東小金井", "武蔵小金井",
                    "国分寺", "西国分寺", "立川", "日野", "豊田", "八王子", "西八王子", "高尾",
                    "東京方面", "高尾・大月方面"),
            train("jr-east.chuo-sobu-local", "JR中央・総武線各駅停車",
                    "三鷹", "吉祥寺", "西荻窪", "荻窪", "阿佐ケ谷", "高円寺", "中野", "東中野",
                    "大久保", "新宿", "代々木", "千駄ケ谷", "信濃町", "四ツ谷", "市ケ谷", "飯田橋",
                    "水道橋", "御茶ノ水", "秋葉原", "浅草橋", "両国", "錦糸町", "亀戸", "平井",
                    "新小岩", "小岩", "市川", "本八幡", "下総中山", "西船橋", "船橋", "東船橋",
                    "津田沼", "幕張本郷", "幕張", "新検見川", "稲毛", "西千葉", "千葉",
                    "三鷹方面", "千葉方面"),
            train("jr-east.sobu-rapid", "JR総武線快速",
                    "東京", "新日本橋", "馬喰町", "錦糸町", "新小岩", "市川", "船橋", "津田沼",
                    "稲毛", "千葉", "都賀", "四街道", "佐倉", "成田", "空港第2ビル", "成田空港",
                    "東京方面", "千葉・成田方面"),
            train("jr-east.yokosuka", "JR横須賀線",
                    "東京", "新橋", "品川", "西大井", "武蔵小杉", "新川崎", "横浜", "保土ケ谷",
                    "東戸塚", "戸塚", "大船", "北鎌倉", "鎌倉", "逗子", "久里浜",
                    "東京方面", "逗子・久里浜方面"),
            train("jr-east.tokaido", "JR東海道線",
                    "東京", "新橋", "品川", "川崎", "横浜", "戸塚", "大船", "藤沢", "辻堂",
                    "茅ケ崎", "平塚", "大磯", "二宮", "国府津", "小田原", "熱海",
                    "東京・上野東京ライン方面", "小田原・熱海方面"),
            train("jr-east.utsunomiya", "JR宇都宮線",
                    "上野", "尾久", "赤羽", "浦和", "さいたま新都心", "大宮", "土呂", "東大宮",
                    "蓮田", "白岡", "新白岡", "久喜", "東鷲宮", "栗橋", "古河", "小山", "宇都宮",
                    "上野東京ライン方面", "宇都宮方面"),
            train("jr-east.takasaki", "JR高崎線",
                    "上野", "尾久", "赤羽", "浦和", "さいたま新都心", "大宮", "宮原", "上尾",
                    "北上尾", "桶川", "北本", "鴻巣", "北鴻巣", "吹上", "行田", "熊谷", "籠原",
                    "深谷", "岡部", "本庄", "神保原", "新町", "高崎",
                    "上野東京ライン方面", "高崎方面"),
            train("jr-east.shonan-shinjuku", "JR湘南新宿ライン",
                    "大宮", "赤羽", "池袋", "新宿", "渋谷", "恵比寿", "大崎", "武蔵小杉", "横浜",
                    "戸塚", "大船", "藤沢", "茅ケ崎", "平塚", "国府津", "小田原",
                    "大宮・宇都宮・高崎方面", "横浜・小田原・逗子方面"),
            train("jr-east.saikyo-kawagoe", "JR埼京・川越線",
                    "大崎", "恵比寿", "渋谷", "新宿", "池袋", "板橋", "十条", "赤羽", "北赤羽",
                    "浮間舟渡", "戸田公園", "戸田", "北戸田", "武蔵浦和", "中浦和", "南与野", "与野本町",
                    "北与野", "大宮", "日進", "指扇", "南古谷", "川越", "西川越", "的場", "笠幡",
                    "武蔵高萩", "高麗川",
                    "大崎・新宿方面", "大宮・川越方面"),
            train("jr-east.keiyo", "JR京葉線",
                    "東京", "八丁堀", "越中島", "潮見", "新木場", "葛西臨海公園", "舞浜", "新浦安",
                    "市川塩浜", "二俣新町", "南船橋", "新習志野", "海浜幕張", "検見川浜", "稲毛海岸",
                    "千葉みなと", "蘇我",
                    "東京方面", "蘇我方面"),
            train("jr-east.musashino", "JR武蔵野線",
                    "府中本町", "北府中", "新小平", "新秋津", "東所沢", "新座", "北朝霞", "西浦和",
                    "武蔵浦和", "南浦和", "東浦和", "東川口", "南越谷", "越谷レイクタウン", "吉川",
                    "新三郷", "三郷", "新松戸", "新八柱", "東松戸", "市川大野", "船橋法典", "西船橋",
                    "府中本町方面", "西船橋方面"),
            train("jr-east.nambu", "JR南武線",
                    "川崎", "尻手", "矢向", "鹿島田", "平間", "向河原", "武蔵小杉", "武蔵中原", "武蔵新城",
                    "武蔵溝ノ口", "津田山", "久地", "宿河原", "登戸", "中野島", "稲田堤", "矢野口", "稲城長沼",
                    "南多摩", "府中本町",
                    "川崎方面", "立川・府中本町方面"),
            train("jr-east.yokohama", "JR横浜線",
                    "東神奈川", "大口", "菊名", "新横浜", "小机", "鴨居", "中山", "十日市場", "長津田",
                    "成瀬", "町田", "古淵", "淵野辺", "矢部", "相模原", "橋本", "相原", "八王子みなみ野",
                    "片倉", "八王子",
                    "東神奈川・横浜方面", "八王子方面"),
            train("jr-east.joban-rapid", "JR常磐線快速",
                    "上野", "日暮里", "三河島", "南千住", "北千住", "松戸", "柏", "我孫子", "取手",
                    "上野東京ライン方面", "取手・土浦方面"),
            train("jr-east.ome", "JR青梅線",
                    "立川", "西立川", "東中神", "中神", "昭島", "拝島", "牛浜", "福生", "羽村", "小作",
                    "河辺", "東青梅", "青梅", "宮ノ平", "日向和田", "石神前", "二俣尾", "軍畑", "沢井",
                    "御嶽", "川井", "古里", "鳩ノ巣", "白丸", "奥多摩",
                    "立川・東京方面", "青梅・奥多摩方面"),

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

    public static List<Service> servicesFor(Provider provider) {
        List<Service> result = new ArrayList<>();
        for (Service service : SERVICES) {
            if (service.provider() == provider) result.add(service);
        }
        return List.copyOf(result);
    }

    private static Service train(String id, String name, String... stopsAndDestinations) {
        int destinationStart = stopsAndDestinations.length - 2;
        return service(id, Provider.JR_EAST, RoutePlan.Mode.TRAIN, name,
                take(stopsAndDestinations, 0, destinationStart),
                take(stopsAndDestinations, destinationStart, stopsAndDestinations.length));
    }

    private static Service bus(String id, String name, String... stops) {
        String first = stops[0];
        String last = stops[stops.length - 1];
        return service(id, Provider.TOEI_BUS, RoutePlan.Mode.BUS, name, List.of(stops),
                List.of(last + "方面", first + "方面"));
    }

    private static Service service(String id, Provider provider, RoutePlan.Mode mode, String name,
                                   List<String> stops, List<String> destinations) {
        return new Service(id, provider, mode, name, stops, destinations);
    }

    private static List<String> take(String[] values, int start, int end) {
        List<String> result = new ArrayList<>();
        for (int index = start; index < end; index++) result.add(values[index]);
        return List.copyOf(result);
    }
}
