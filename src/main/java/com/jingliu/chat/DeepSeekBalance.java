package com.jingliu.chat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class DeepSeekBalance {
    private final Map<String, BigDecimal> totals = new TreeMap<>();
    private int keys;

    void add(JSONObject payload) throws JSONException {
        JSONArray entries = payload.getJSONArray("balance_infos");
        if (entries.length() == 0) throw new JSONException("Missing balance entries");
        Map<String, BigDecimal> keyTotals = new TreeMap<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String currency = entry.getString("currency");
            String amount = entry.getString("total_balance");
            if ((!"CNY".equals(currency) && !"USD".equals(currency)) || keyTotals.containsKey(currency)
                || !amount.matches("-?[0-9]{1,20}(\\.[0-9]{1,12})?")) {
                throw new JSONException("Invalid balance entry");
            }
            keyTotals.put(currency, new BigDecimal(amount));
        }
        keyTotals.forEach((currency, amount) -> totals.merge(currency, amount, BigDecimal::add));
        keys++;
    }

    String label() {
        if (totals.isEmpty()) return "";
        StringBuilder label = new StringBuilder(keys == 1 ? "余额 " : "密钥合计 ");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            if (!first) label.append(" · ");
            BigDecimal amount = entry.getValue().stripTrailingZeros();
            label.append("CNY".equals(entry.getKey()) ? "¥" : "US$")
                .append(amount.setScale(Math.max(2, amount.scale())).toPlainString());
            first = false;
        }
        return label.toString();
    }
}
