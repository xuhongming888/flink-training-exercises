package com.dataartisans.flinktraining.xzh.lab1.transform01.statless;

import com.dataartisans.flinktraining.exercises.datastream_java.datatypes.TaxiRide;
import com.dataartisans.flinktraining.exercises.datastream_java.sources.TaxiRideSource;
import com.dataartisans.flinktraining.exercises.datastream_java.utils.ExerciseBase;
import com.dataartisans.flinktraining.exercises.datastream_java.utils.GeoUtils;
import com.dataartisans.flinktraining.xzh.lab1.datatypes.EnrichedRide;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import static com.dataartisans.flinktraining.exercises.datastream_java.utils.ExerciseBase.rideSourceOrTest;

public class FlatMapExercise {
    public static void main(String[] args) throws Exception{

        ParameterTool params = ParameterTool.fromArgs(args);
        final String input = params.get("input", ExerciseBase.pathToRideData);

        final int maxEventDelay = 60;       // events are out of order by max 60 seconds
        final int servingSpeedFactor = 600; // events of 10 minutes are served in 1 second

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(ExerciseBase.parallelism);

        DataStream<TaxiRide> rides = env.addSource(rideSourceOrTest(new TaxiRideSource(input, maxEventDelay, servingSpeedFactor)));

        DataStream<TaxiRide> filteredRides = rides.filter(new NYCFilter());

        DataStream<EnrichedRide> enrichedNYCRides = filteredRides.flatMap(new NYCEnrichment());

        enrichedNYCRides.print();

        env.execute("Enriched Taxi Ride Cleansing");
    }

    private static class NYCFilter implements FilterFunction<TaxiRide> {

        @Override
        public boolean filter(TaxiRide taxiRide) {
            return GeoUtils.isInNYC(taxiRide.startLon, taxiRide.startLat) &&
                    GeoUtils.isInNYC(taxiRide.endLon, taxiRide.endLat);
        }
    }

    private static class NYCEnrichment implements FlatMapFunction<TaxiRide, EnrichedRide>{
        @Override
        public void flatMap(TaxiRide ride, Collector<EnrichedRide> collector) throws Exception {
            collector.collect(new EnrichedRide(ride));
        }
    }


}
