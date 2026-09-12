package jp.sugunoru.app.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One actual direction/branch/loop, never an inferred reversal of a stop list. */
public record BusRoutePattern(String id, List<Stop> stops) {
    public record Stop(int index, String pole, String name, boolean canGetOn, boolean canGetOff) {
        public Stop {
            if (index < 0 || pole == null || pole.isBlank() || name == null || name.isBlank()) {
                throw new IllegalArgumentException("停留所データが不完全です");
            }
        }
    }

    public BusRoutePattern {
        if (id == null || id.isBlank() || stops == null || stops.size() < 2) {
            throw new IllegalArgumentException("経路データが不完全です");
        }
        List<Stop> ordered = new ArrayList<>(stops);
        ordered.sort(Comparator.comparingInt(Stop::index));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).index() == ordered.get(i).index()) {
                throw new IllegalArgumentException("停留所の順番が重複しています");
            }
        }
        stops = List.copyOf(ordered);
    }

    public static List<String> boardingStops(List<BusRoutePattern> patterns) {
        Set<String> names = new LinkedHashSet<>();
        for (BusRoutePattern pattern : patterns) {
            for (Stop stop : pattern.stops) {
                if (stop.canGetOn() && !alightingStops(List.of(pattern), stop.name()).isEmpty()) {
                    names.add(stop.name());
                }
            }
        }
        return List.copyOf(names);
    }

    public static List<String> alightingStops(List<BusRoutePattern> patterns, String boarding) {
        Set<String> names = new LinkedHashSet<>();
        for (BusRoutePattern pattern : patterns) {
            boolean boarded = false;
            for (Stop stop : pattern.stops) {
                if (boarded && stop.canGetOff() && !stop.name().equals(boarding)) names.add(stop.name());
                if (stop.canGetOn() && stop.name().equals(boarding)) boarded = true;
            }
        }
        return List.copyOf(names);
    }

    public boolean serves(String boarding, String alighting) {
        return alightingStops(List.of(this), boarding).contains(alighting);
    }
}
