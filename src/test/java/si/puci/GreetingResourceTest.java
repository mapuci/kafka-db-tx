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
    void testHelloEndpoint()
    {
        AtomicBoolean failure = new AtomicBoolean(false);
        AtomicReference<Throwable> ex = new AtomicReference<>(null);

        final var subscription = Multi.createFrom().ticks().every(Duration.of(1, ChronoUnit.MILLIS))
                .onItem().invoke(i -> given()
                        .when().get("/hello")
                        .then()
                        .statusCode(200)
                        .body(is("Hello from RESTEasy Reactive")))
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