package com.dataartisans.flinktraining.xzh.lab1.transform02.key;

import com.dataartisans.flinktraining.exercises.datastream_java.datatypes.TaxiRide;
import com.dataartisans.flinktraining.exercises.datastream_java.sources.TaxiRideSource;
import com.dataartisans.flinktraining.exercises.datastream_java.utils.ExerciseBase;
import com.dataartisans.flinktraining.exercises.datastream_java.utils.GeoUtils;
import com.dataartisans.flinktraining.xzh.lab1.datatypes.EnrichedRide;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.joda.time.Interval;
import org.joda.time.Minutes;

import static com.dataartisans.flinktraining.exercises.datastream_java.utils.ExerciseBase.rideSourceOrTest;

public class KeyByExercise {
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

        DataStream<EnrichedRide> enrichedNYCRides = filteredRides.map(new Enrichment());
        /**
         *使用lambda表达式，需要做明确返回类型，否则出现"The generic type parameters of 'Collector' are missing."异常
         * 详情：https://ci.apache.org/projects/flink/flink-docs-stable/dev/java_lambdas.html
         */
        DataStream<Tuple2<Integer, Minutes>> minutesByStartCell = enrichedNYCRides
                .flatMap(new FlatMapFunction<EnrichedRide, Tuple2<Integer, Minutes>>() {
                    @Override
                    public void flatMap(EnrichedRide ride,
                                        Collector<Tuple2<Integer, Minutes>> out) throws Exception {
                        if (!ride.isStart) {
                            Interval rideInterval = new Interval(ride.startTime, ride.endTime);
                            Minutes duration = rideInterval.toDuration().toStandardMinutes();
                            out.collect(new Tuple2<>(ride.startCell, duration));
                        }
                    }
                });

        minutesByStartCell.keyBy(0)
                .maxBy(1)
                .print();


        env.execute("Enriched Taxi Ride Cleansing");
    }

    private static class NYCFilter implements FilterFunction<TaxiRide> {

        @Override
        public boolean filter(TaxiRide taxiRide) {
            return GeoUtils.isInNYC(taxiRide.startLon, taxiRide.startLat) &&
                    GeoUtils.isInNYC(taxiRide.endLon, taxiRide.endLat);
        }
    }

    private static class Enrichment implements MapFunction<TaxiRide, EnrichedRide> {
        @Override
        public EnrichedRide map(TaxiRide taxiRide) {
            return new EnrichedRide(taxiRide);
        }
    }
}
