package com.hchen.superlyric.provider;

import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class QQTimelineFieldsTest {
    static class Row {
        int index;
        long duration;
        long start;
        long end;
        Row(int i, long d, long s) { index = i; duration = d; start = s; end = s + d; }
    }
    private List<Field> fields() {
        List<Field> fields = new ArrayList<>(Arrays.asList(Row.class.getDeclaredFields()));
        fields.forEach(f -> f.setAccessible(true));
        return fields;
    }
    @Test public void indexAndConstantDurationMustNotBeSelectedAsStart() {
        List<Row> rows = Arrays.asList(new Row(0, 3000, 0), new Row(1, 3000, 7000), new Row(2, 3000, 14000));
        Field start = QQTimelineFields.start(rows, fields());
        assertEquals("start", start.getName());
        assertEquals("duration", QQTimelineFields.duration(rows, fields(), start).getName());
    }
    @Test public void examineFullTimelineBeyondLeadingRepeatedSamples() {
        List<Row> rows = new ArrayList<>();
        for(int i=0;i<16;i++) rows.add(new Row(i, 0, 0));
        rows.add(new Row(16, 2000, 15000));
        rows.add(new Row(17, 3000, 22000));
        assertEquals("start", QQTimelineFields.start(rows, fields()).getName());
    }
    @Test public void absoluteEndIsNotDuration() throws Exception {
        List<Row> rows = Arrays.asList(new Row(0, 3000, 10000), new Row(1, 3000, 20000), new Row(2, 3000, 30000));
        List<Field> fields = fields();
        fields.removeIf(f -> f.getName().equals("duration"));
        assertNull(QQTimelineFields.duration(rows, fields, Row.class.getDeclaredField("start")));
    }
    @Test public void ambiguousSingleRowIsNotCachedAsAFullTimeline() {
        assertNull(QQTimelineFields.start(Arrays.asList(new Row(0, 3000, 0)), fields()));
    }
}
