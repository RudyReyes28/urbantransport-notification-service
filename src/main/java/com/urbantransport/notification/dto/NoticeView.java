package com.urbantransport.notification.dto;

import lombok.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contenido de un correo con el layout común (notice.html). */
@Value
public class NoticeView {
    Tone tone;
    String heading;
    String intro;
    List<NoticeRow> rows;
    String note;
    String actionLabel;
    String actionUrl;

    public static NoticeView of(Tone tone, String heading, String intro, List<NoticeRow> rows, String note) {
        return new NoticeView(tone, heading, intro, rows == null ? List.of() : rows, note, null, null);
    }

    public static NoticeView withAction(Tone tone, String heading, String intro, List<NoticeRow> rows, String note,
                                        String actionLabel, String actionUrl) {
        return new NoticeView(tone, heading, intro, rows == null ? List.of() : rows, note, actionLabel, actionUrl);
    }

    public Map<String, Object> toVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("accent", tone.getHex());
        variables.put("heading", heading);
        variables.put("intro", intro);
        variables.put("rows", rows);
        variables.put("note", note);
        variables.put("actionLabel", actionLabel);
        variables.put("actionUrl", actionUrl);
        return variables;
    }
}