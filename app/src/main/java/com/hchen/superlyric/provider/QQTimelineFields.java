package com.hchen.superlyric.provider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identify timestamps from a full timeline, never from numeric declaration order. */
final class QQTimelineFields {
    private QQTimelineFields() {}

    static Field start(List<?> lines, List<Field> fields) {
        Map<Field, long[]> candidates = new LinkedHashMap<>();
        Field best = null;
        long bestSpan = -1;
        long bestFirst = Long.MAX_VALUE;
        for (Field field : fields) {
            long first = -1, previous = -1;
            int count = 0, increases = 0;
            boolean valid = true;
            for (Object line : lines) {
                if (line == null) continue;
                try {
                    long value = ((Number) field.get(line)).longValue();
                    if (value < 0 || (count > 0 && value < previous)) { valid = false; break; }
                    if (count == 0) first = value;
                    else if (value > previous) increases++;
                    previous = value;
                    count++;
                } catch (ReflectiveOperationException | ClassCastException error) {
                    valid = false; break;
                }
            }
            long span = previous - first;
            // Row indices and constant durations are monotonic too, but are not milliseconds.
            if (!valid || count < 2 || increases == 0 || span < 1000 || span <= count * 10L) continue;
            candidates.put(field, new long[]{span, first});
        }
        for (Map.Entry<Field, long[]> candidate : candidates.entrySet()) {
            Field field = candidate.getKey();
            long span = candidate.getValue()[0], first = candidate.getValue()[1];
            boolean absoluteEnd = false;
            for (Map.Entry<Field, long[]> other : candidates.entrySet()) {
                if (other.getKey().equals(field) || other.getValue()[0] < span * 0.5) continue;
                boolean greater = false, ordered = true;
                for (Object line : lines) {
                    if (line == null) continue;
                    try {
                        long value = ((Number) field.get(line)).longValue();
                        long earlier = ((Number) other.getKey().get(line)).longValue();
                        if (value < earlier) { ordered = false; break; }
                        if (value > earlier) greater = true;
                    } catch (ReflectiveOperationException error) { ordered = false; break; }
                }
                if (ordered && greater) { absoluteEnd = true; break; }
            }
            if (absoluteEnd) continue;
            if (best == null || span > bestSpan * 1.1 ||
                (span >= bestSpan * 0.9 && first < bestFirst)) {
                best = field; bestSpan = span; bestFirst = first;
            }
        }
        return best;
    }

    static Field duration(List<?> lines, List<Field> fields, Field start) {
        Field best = null;
        long bestTotal = 0;
        for (Field field : fields) {
            if (field.equals(start)) continue;
            long total = 0, maximum = 0;
            boolean valid = true;
            for (int i = 0; i < lines.size(); i++) {
                Object line = lines.get(i);
                if (line == null) continue;
                try {
                    long value = ((Number) field.get(line)).longValue();
                    long begin = ((Number) start.get(line)).longValue();
                    if (value < 0 || value > 120000) { valid = false; break; }
                    // An absolute end timestamp is not a duration.
                    if (i + 1 < lines.size() && lines.get(i + 1) != null) {
                        long next = ((Number) start.get(lines.get(i + 1))).longValue();
                        if (next > begin && value > next - begin + 2000) { valid = false; break; }
                    }
                    maximum = Math.max(maximum, value);
                    total += value;
                } catch (ReflectiveOperationException | ClassCastException error) { valid = false; break; }
            }
            if (valid && maximum >= 100 && maximum > lines.size() * 10L && total > bestTotal) {
                best = field; bestTotal = total;
            }
        }
        return best;
    }
}
