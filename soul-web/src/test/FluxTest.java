import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class FluxTest {


    public static void sout(String msg) {
        System.out.println(msg);
    }

    public static <T> void sout(T msg) {
        System.out.println(msg);
    }

    public static void sout(Integer msg) {
        System.out.println(msg);
    }


    public void test001() {

        Flux.just("Hello", "World").subscribe(FluxTest::sout);
        Flux.fromArray(new Integer[]{1, 2, 3}).subscribe(FluxTest::sout);
        Flux.empty().subscribe(FluxTest::sout);
        Flux.range(1, 10).subscribe(FluxTest::sout);
        Flux.interval(Duration.of(5, ChronoUnit.SECONDS)).subscribe(FluxTest::sout);

    }


    public void test002() {

        Flux.generate(sink -> {
            sink.next("Hello");
            //sink.complete();
        }).subscribe(System.out::println);
    }

    public void test003() {
        Flux.create(sink -> {
            for (int i = 0; i < 5; i++) {
                sink.next(i);
            }
            for (int i = 0; i < 5; i++) {
                sink.next(i + "*" + i + "=" + i * i);
            }
            sink.complete();
        }).subscribe(System.out::println);
    }

    public void test004() {
        Mono.fromSupplier(() -> "Hello").subscribe(System.out::println);
        Mono.justOrEmpty(Optional.of("Hello")).subscribe(System.out::println);
        Mono.create(sink -> sink.success("Hello")).subscribe(System.out::println);
        Mono.create(sink -> {
            for (int i = 4; i < 5; i++) {
                sink.success(i + "*" + i + "=" + i * i);
            }
        }).subscribe(System.out::println);
    }

    public void test005() {
        Flux.range(1, 100).buffer(20).subscribe(System.out::println);
        Flux.interval(Duration.ofMillis(100)).bufferTimeout(5, Duration.ofMillis(400)).take(5).toStream().forEach(System.out::println);
        Flux.range(1, 10).bufferUntil(i -> i % 2 == 0).subscribe(System.out::println);
        Flux.range(1, 10).bufferWhile(i -> i % 2 == 0).subscribe(System.out::println);
        Flux.range(1, 10).filter(i -> i % 2 == 0).subscribe(System.out::println);
    }

    public void test006() {
        Flux.range(1, 1000).take(10).subscribe(System.out::println);
        Flux.range(1, 1000).takeLast(10).subscribe(System.out::println);
        Flux.range(1, 1000).takeWhile(i -> i < 10).subscribe(System.out::println);
        Flux.range(1, 1000).takeUntil(i -> i % 20 == 0).subscribe(System.out::println);
    }

    public void test007() {
        Flux.range(1, 100).reduce((x, y) -> x + y).subscribe(System.out::println);
        Flux.range(1, 100).reduceWith(() -> 100, (x, y) -> x + y).subscribe(System.out::println);
    }

    public void test008() {
        Flux.range(1, 100).reduce((x, y) -> x + y).subscribe(System.out::println);
        Flux.range(1, 100).reduceWith(() -> 100, (x, y) -> x + y).subscribe(System.out::println);
    }

    public static void main(String[] args) {
        FluxTest fluxTest = new FluxTest();
        fluxTest.test007();
        try {
            Thread.sleep(2000);
        } catch (Exception e) {

        }
    }
}
