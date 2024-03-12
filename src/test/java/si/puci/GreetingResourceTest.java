package si.puci;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@QuarkusTest
class GreetingResourceTest
{
    @Test
    void mutinyEmitterSendAndAwait()
    {
        //this throws 1:1000

        AtomicBoolean failure = new AtomicBoolean(false);
        AtomicReference<Throwable> ex = new AtomicReference<>(null);

        final var subscription = Multi.createFrom().ticks().every(Duration.of(1, ChronoUnit.MILLIS))
                .onItem().invoke(i -> given()
                        .when().get("/hello/mutiny-emitter-send-and-await")
                        .then()
                        .statusCode(200)
                        .body(is("mutiny-emitter-send-and-await")))
                .subscribe().with(
                        Log::info,
                        err -> {
                            Log.error(err);
                            ex.set(err);
                            failure.set(true);
                        });
        try
        {
            Thread.sleep(60000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        subscription.cancel();
        if (ex.get() != null)
        {
            Log.error(ex.get());
        }
        Assertions.assertFalse(failure.get());
        //no error!
    }
    @Test
    void mutinyEmitterSendAndForget()
    {
        //this always throws
        given()
                .when().get("/hello/mutiny-emitter-send-and-forget")
                .then()
                .statusCode(200)
                .body(is("mutiny-emitter-send-and-forget"));
    }

    @Test
    void emitterSendBlockAtEnd()
    {
        //this throws 1:1000

        AtomicBoolean failure = new AtomicBoolean(false);
        AtomicReference<Throwable> ex = new AtomicReference<>(null);

        final var subscription = Multi.createFrom().ticks().every(Duration.of(1, ChronoUnit.MILLIS))
                .onItem().invoke(i -> given()
                        .when().get("/hello/emitter-send-block-at-end")
                        .then()
                        .statusCode(200)
                        .body(is("emitter-send-block-at-end")))
                .subscribe().with(
                        Log::info,
                        err -> {
                            Log.error(err);
                            ex.set(err);
                            failure.set(true);
                        });
        try
        {
            Thread.sleep(60000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        subscription.cancel();
        if (ex.get() != null)
        {
            Log.error(ex.get());
        }
        Assertions.assertFalse(failure.get());
        //no error!
    }
    @Test
    void emitterSendReturnFut()
    {
        //this never throws.

        AtomicBoolean failure = new AtomicBoolean(false);
        AtomicReference<Throwable> ex = new AtomicReference<>(null);

        final var subscription = Multi.createFrom().ticks().every(Duration.of(1, ChronoUnit.MILLIS))
                .onItem().invoke(i -> given()
                        .when().get("/hello/emitter-send-return-fut")
                        .then()
                        .statusCode(200)
                        .body(is("emitter-send-return-fut")))
                .subscribe().with(
                        Log::info,
                        err -> {
                            Log.error(err);
                            ex.set(err);
                            failure.set(true);
                        });
        try
        {
            Thread.sleep(60000);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        subscription.cancel();
        if (ex.get() != null)
        {
            Log.error(ex.get());
        }
        Assertions.assertFalse(failure.get());
        //no error!
    }
}