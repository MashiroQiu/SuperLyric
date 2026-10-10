package com.hchen.superlyric.publisher;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class PlaybackClockTest {
    private long estimate(int state, long position, float speed, long anchor, long now) throws Exception {
        Class<?> snapshot = Class.forName(PlaybackTracker.class.getName() + "$PlaybackSnapshot");
        Constructor<?> constructor = snapshot.getDeclaredConstructor(int.class,long.class,float.class,long.class);
        constructor.setAccessible(true);
        Method method = PlaybackTracker.class.getDeclaredMethod("estimatePosition",snapshot,long.class);
        method.setAccessible(true);
        return (Long) method.invoke(null,constructor.newInstance(state,position,speed,anchor),now);
    }
    @Test public void pausedClockCannotDriftToFinalLine() throws Exception {
        assertEquals(1000,estimate(2,1000,1f,1000,301000));
    }
    @Test public void playingClockAdvancesFromItsTimestamp() throws Exception {
        assertEquals(4500,estimate(3,3000,1f,10000,11500));
    }
    @Test public void nonFiniteSpeedCannotOverflowTimeline() throws Exception {
        assertEquals(3000,estimate(3,3000,Float.POSITIVE_INFINITY,10000,11500));
    }
}
