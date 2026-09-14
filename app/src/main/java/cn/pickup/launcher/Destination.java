package cn.pickup.launcher;

import java.util.Locale;

enum Destination {
    CAINIAO(
            "cainiao",
            "菜鸟身份码",
            "菜",
            "com.cainiao.wireless",
            new String[] {
                    "cainiao://desktop/station_code"
            },
            "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO",
            true
    ),
    CAINIAO_PACKAGES(
            "cainiao_packages",
            "菜鸟包裹",
            "菜",
            "com.cainiao.wireless",
            new String[] {
                    "cainiao://"
            },
            "https://www.guoguo-app.com/"
    ),
    TAOBAO(
            "taobao",
            "淘宝身份码",
            "淘",
            "com.taobao.taobao",
            new String[] {
                    "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fmarket.m.taobao.com%2Fapp%2Fcn-yz%2Fmulti-activity%2FauthCode.html",
                    "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fmarket.m.taobao.com%2Fapp%2Fcn-yz%2Fmulti-activity%2FauthCode.html"
            },
            "https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html"
    ),
    TAOBAO_PENDING(
            "taobao_pending",
            "淘宝订单",
            "淘",
            "com.taobao.taobao",
            new String[] {
                    "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fh5.m.taobao.com%2Fmlapp%2Folist.html%3FtabCode%3DwaitConfirm",
                    "taobao://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fh5.m.taobao.com%2Fmlapp%2Folist.html",
                    "tbopen://m.taobao.com/tbopen/index.html?h5Url=https%3A%2F%2Fh5.m.taobao.com%2Fmlapp%2Folist.html"
            },
            "https://h5.m.taobao.com/mlapp/olist.html?tabCode=waitConfirm"
    ),
    PINDUODUO(
            "pinduoduo",
            "拼多多身份码",
            "拼",
            "com.xunmeng.pinduoduo",
            new String[] {
                    "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11&refer_page_name=login&refer_page_sn=10169",
                    "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11",
                    "pinduoduo://com.xunmeng.pinduoduo/mdkd/package"
            },
            "https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0"
    ),
    PINDUODUO_PENDING(
            "pinduoduo_pending",
            "拼多多订单",
            "拼",
            "com.xunmeng.pinduoduo",
            new String[] {
                    "pinduoduo://com.xunmeng.pinduoduo/orders.html?type=3&comment_tab=1&combine_orders=1&main_orders=1&refer_page_name=personal",
                    "pinduoduo://com.xunmeng.pinduoduo/orders.html",
                    "pinduoduo://com.xunmeng.pinduoduo/index.html?index=4&pr_tab_link=personal.html"
            },
            "https://mobile.yangkeduo.com/orders.html"
    ),
    JD(
            "jd",
            "京东待取快递",
            "京",
            "com.jingdong.app.mall",
            new String[] {
                    "openjd://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22orderlist%22%7D"
            },
            "https://order.jd.com/center/list.action"
    ),
    XHS(
            "xhs",
            "小红书待取快递",
            "书",
            "com.xingin.xhs",
            new String[] {
                    "xhsdiscover://rn/lancer-order/order/list"
            },
            "https://www.xiaohongshu.com/"
    ),
    DOUYIN_PENDING(
            "douyin_pending",
            "抖音订单",
            "抖",
            "com.ss.android.ugc.aweme",
            new String[] {
                    "snssdk1128://",
                    "snssdk1128://search/tabs?keyword=%E6%88%91%E7%9A%84%E8%AE%A2%E5%8D%95",
                    "snssdk1128://feed?refer=web"
            },
            "https://haohuo.jinritemai.com/views/pages/index/order-list",
            true
    ),
    KUAISHOU_PENDING(
            "kuaishou_pending",
            "快手订单",
            "快",
            "com.smile.gifmaker",
            new String[] {
                    "ksnebula://",
                    "kwai://merchanthome",
                    "kwai://home",
                    "kwai://"
            },
            "https://www.kwaixiaodian.com/",
            true
    );

    final String key;
    final String title;
    final String mark;
    final String packageName;
    final String[] appUris;
    final String webUri;
    final boolean openAppWhenDeepLinkUnavailable;

    Destination(
            String key,
            String title,
            String mark,
            String packageName,
            String[] appUris,
            String webUri
    ) {
        this(key, title, mark, packageName, appUris, webUri, false);
    }

    Destination(
            String key,
            String title,
            String mark,
            String packageName,
            String[] appUris,
            String webUri,
            boolean openAppWhenDeepLinkUnavailable
    ) {
        this.key = key;
        this.title = title;
        this.mark = mark;
        this.packageName = packageName;
        this.appUris = appUris;
        this.webUri = webUri;
        this.openAppWhenDeepLinkUnavailable = openAppWhenDeepLinkUnavailable;
    }

    String[] candidatePackages() {
        switch (this) {
            case DOUYIN_PENDING:
                return new String[] {"com.ss.android.ugc.livelite", "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite"};
            case KUAISHOU_PENDING:
                return new String[] {"com.smile.gifmaker", "com.kuaishou.nebula"};
            default:
                return new String[] {packageName};
        }
    }

    static Destination fromKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (Destination destination : values()) {
            if (destination.key.equals(normalized)) {
                return destination;
            }
        }
        return null;
    }
}
