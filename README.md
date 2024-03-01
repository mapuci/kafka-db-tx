# Reproduce wierd error with blocking db TX when sending kafka message in it.

```shell script
./mvnw test
```

Test runs for 1 minute, it performs kind of stress test, during which the following exception occurs. 
The test just fires REST request every 1ms, the request inserts something into db and writes message to kafka.

In my case exception occurs in ~~90% of cases, if it doesn't happen retry. It happens randomly.

I investigated a bit further, placing breakpoint at 
`/Users/matejpucihar/.m2/repository/org/jboss/narayana/jta/narayana-jta/7.0.0.Final/narayana-jta-7.0.0.Final.jar!/com/arjuna/ats/arjuna/logging/arjunaI18NLogger_$logger.class:693`
and
`com.arjuna.ats.arjuna.coordinator.BasicAction.checkChildren`
where I found the 2 threads that are associated with TX. `worker thread` and `kafka-producer-thread`.

The issue can kind of be solved with usage of executor with no propagated transaction context.

```
2024-03-01 08:32:55,147 INFO  [io.sma.mut.sub.Subscribers$CallbackBasedSubscriber] (executor-thread-2) 8949
2024-03-01 08:32:55,151 INFO  [io.sma.mut.sub.Subscribers$CallbackBasedSubscriber] (executor-thread-1) 8950
2024-03-01 08:32:55,157 INFO  [io.sma.mut.sub.Subscribers$CallbackBasedSubscriber] (executor-thread-2) 8951
2024-03-01 08:32:55,162 WARN  [com.arj.ats.arjuna] (executor-thread-2) ARJUNA012094: Commit of action id 0:ffff0a010a48:e441:65e18477:d1d0 invoked while multiple threads active within it.
2024-03-01 08:32:55,163 WARN  [com.arj.ats.arjuna] (executor-thread-2) ARJUNA012107: CheckedAction::check - atomic action 0:ffff0a010a48:e441:65e18477:d1d0 commiting with 2 threads active!
2024-03-01 08:32:55,163 WARN  [com.arj.ats.jta] (executor-thread-2) ARJUNA016039: onePhaseCommit on < formatId=131077, gtrid_length=35, bqual_length=36, tx_uid=0:ffff0a010a48:e441:65e18477:d1d0, node_name=quarkus, branch_uid=0:ffff0a010a48:e441:65e18477:d1d3, subordinatenodename=null, eis_name=0 > (io.agroal.narayana.LocalXAResource@2ea67dc) failed with exception XAException.XA_RBROLLBACK: javax.transaction.xa.XAException: Error trying to transactionCommit local transaction: Enlisted connection used without active transaction
	at io.agroal.narayana.LocalXAResource.xaException(LocalXAResource.java:140)
	at io.agroal.narayana.LocalXAResource.xaException(LocalXAResource.java:134)
	at io.agroal.narayana.LocalXAResource.commit(LocalXAResource.java:72)
	at com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord.topLevelOnePhaseCommit(XAResourceRecord.java:678)
	at com.arjuna.ats.arjuna.coordinator.BasicAction.onePhaseCommit(BasicAction.java:2457)
	at com.arjuna.ats.arjuna.coordinator.BasicAction.End(BasicAction.java:1520)
	at com.arjuna.ats.arjuna.coordinator.TwoPhaseCoordinator.end(TwoPhaseCoordinator.java:74)
	at com.arjuna.ats.arjuna.AtomicAction.commit(AtomicAction.java:138)
	at com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple.commitAndDisassociate(TransactionImple.java:1271)
	at com.arjuna.ats.internal.jta.transaction.arjunacore.BaseTransaction.commit(BaseTransaction.java:104)
	at io.quarkus.narayana.jta.runtime.NotifyingTransactionManager.commit(NotifyingTransactionManager.java:70)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.endTransaction(TransactionalInterceptorBase.java:406)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.invokeInOurTx(TransactionalInterceptorBase.java:171)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.invokeInOurTx(TransactionalInterceptorBase.java:107)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired.doIntercept(TransactionalInterceptorRequired.java:38)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.intercept(TransactionalInterceptorBase.java:61)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired.intercept(TransactionalInterceptorRequired.java:32)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired_Bean.intercept(Unknown Source)
	at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:42)
	at io.quarkus.arc.impl.AroundInvokeInvocationContext.perform(AroundInvokeInvocationContext.java:30)
	at io.quarkus.arc.impl.InvocationContexts.performAroundInvoke(InvocationContexts.java:27)
	at si.puci.GreetingResource_Subclass.hello(Unknown Source)
	at si.puci.GreetingResource_ClientProxy.hello(Unknown Source)
	at si.puci.GreetingResource$quarkusrestinvoker$hello_e747664148511e1e5212d3e0f4b40d45c56ab8a1.invoke(Unknown Source)
	at org.jboss.resteasy.reactive.server.handlers.InvocationHandler.handle(InvocationHandler.java:29)
	at io.quarkus.resteasy.reactive.server.runtime.QuarkusResteasyReactiveRequestContext.invokeHandler(QuarkusResteasyReactiveRequestContext.java:141)
	at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.run(AbstractResteasyReactiveContext.java:147)
	at io.quarkus.vertx.core.runtime.VertxCoreRecorder$14.runWith(VertxCoreRecorder.java:582)
	at org.jboss.threads.EnhancedQueueExecutor$Task.run(EnhancedQueueExecutor.java:2513)
	at org.jboss.threads.EnhancedQueueExecutor$ThreadBody.run(EnhancedQueueExecutor.java:1538)
	at org.jboss.threads.DelegatingRunnable.run(DelegatingRunnable.java:29)
	at org.jboss.threads.ThreadLocalResettingRunnable.run(ThreadLocalResettingRunnable.java:29)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: java.sql.SQLException: Enlisted connection used without active transaction
	at io.agroal.pool.ConnectionHandler.verifyEnlistment(ConnectionHandler.java:398)
	at io.agroal.pool.ConnectionHandler.transactionCommit(ConnectionHandler.java:355)
	at io.agroal.narayana.LocalXAResource.commit(LocalXAResource.java:69)
	... 31 more

2024-03-01 08:32:55,169 ERROR [io.qua.ver.htt.run.QuarkusErrorHandler] (executor-thread-2) HTTP Request to /hello failed, error id: 2795b427-72e0-40e2-84da-2e4b8d692231-1: io.quarkus.arc.ArcUndeclaredThrowableException: Error invoking subclass method
	at si.puci.GreetingResource_Subclass.hello(Unknown Source)
	at si.puci.GreetingResource_ClientProxy.hello(Unknown Source)
	at si.puci.GreetingResource$quarkusrestinvoker$hello_e747664148511e1e5212d3e0f4b40d45c56ab8a1.invoke(Unknown Source)
	at org.jboss.resteasy.reactive.server.handlers.InvocationHandler.handle(InvocationHandler.java:29)
	at io.quarkus.resteasy.reactive.server.runtime.QuarkusResteasyReactiveRequestContext.invokeHandler(QuarkusResteasyReactiveRequestContext.java:141)
	at org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext.run(AbstractResteasyReactiveContext.java:147)
	at io.quarkus.vertx.core.runtime.VertxCoreRecorder$14.runWith(VertxCoreRecorder.java:582)
	at org.jboss.threads.EnhancedQueueExecutor$Task.run(EnhancedQueueExecutor.java:2513)
	at org.jboss.threads.EnhancedQueueExecutor$ThreadBody.run(EnhancedQueueExecutor.java:1538)
	at org.jboss.threads.DelegatingRunnable.run(DelegatingRunnable.java:29)
	at org.jboss.threads.ThreadLocalResettingRunnable.run(ThreadLocalResettingRunnable.java:29)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: jakarta.transaction.RollbackException: ARJUNA016053: Could not commit transaction.
	at com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple.commitAndDisassociate(TransactionImple.java:1283)
	at com.arjuna.ats.internal.jta.transaction.arjunacore.BaseTransaction.commit(BaseTransaction.java:104)
	at io.quarkus.narayana.jta.runtime.NotifyingTransactionManager.commit(NotifyingTransactionManager.java:70)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.endTransaction(TransactionalInterceptorBase.java:406)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.invokeInOurTx(TransactionalInterceptorBase.java:171)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.invokeInOurTx(TransactionalInterceptorBase.java:107)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired.doIntercept(TransactionalInterceptorRequired.java:38)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorBase.intercept(TransactionalInterceptorBase.java:61)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired.intercept(TransactionalInterceptorRequired.java:32)
	at io.quarkus.narayana.jta.runtime.interceptor.TransactionalInterceptorRequired_Bean.intercept(Unknown Source)
	at io.quarkus.arc.impl.InterceptorInvocation.invoke(InterceptorInvocation.java:42)
	at io.quarkus.arc.impl.AroundInvokeInvocationContext.perform(AroundInvokeInvocationContext.java:30)
	at io.quarkus.arc.impl.InvocationContexts.performAroundInvoke(InvocationContexts.java:27)
	... 13 more
	Suppressed: javax.transaction.xa.XAException: Error trying to transactionCommit local transaction: Enlisted connection used without active transaction
		at io.agroal.narayana.LocalXAResource.xaException(LocalXAResource.java:140)
		at io.agroal.narayana.LocalXAResource.xaException(LocalXAResource.java:134)
		at io.agroal.narayana.LocalXAResource.commit(LocalXAResource.java:72)
		at com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord.topLevelOnePhaseCommit(XAResourceRecord.java:678)
		at com.arjuna.ats.arjuna.coordinator.BasicAction.onePhaseCommit(BasicAction.java:2457)
		at com.arjuna.ats.arjuna.coordinator.BasicAction.End(BasicAction.java:1520)
		at com.arjuna.ats.arjuna.coordinator.TwoPhaseCoordinator.end(TwoPhaseCoordinator.java:74)
		at com.arjuna.ats.arjuna.AtomicAction.commit(AtomicAction.java:138)
		at com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple.commitAndDisassociate(TransactionImple.java:1271)
		... 25 more
	Caused by: java.sql.SQLException: Enlisted connection used without active transaction
		at io.agroal.pool.ConnectionHandler.verifyEnlistment(ConnectionHandler.java:398)
		at io.agroal.pool.ConnectionHandler.transactionCommit(ConnectionHandler.java:355)
		at io.agroal.narayana.LocalXAResource.commit(LocalXAResource.java:69)
		... 31 more

2024-03-01 08:32:55,179 ERROR [si.puc.GreetingResourceTest] (executor-thread-1) java.lang.AssertionError: 1 expectation failed.
Expected status code <200> but was <500>.

2024-03-01 08:32:55,180 ERROR [io.qua.mut.run.MutinyInfrastructure] (executor-thread-1) Mutiny had to drop the following exception: io.smallrye.mutiny.CompositeException: Multiple exceptions caught:
	[Exception 0] java.lang.AssertionError: 1 expectation failed.
Expected status code <200> but was <500>.

	[Exception 1] org.opentest4j.AssertionFailedError
	at io.smallrye.mutiny.subscription.Subscribers$CallbackBasedSubscriber.onFailure(Subscribers.java:95)
	at io.smallrye.mutiny.operators.multi.MultiOperatorProcessor.onFailure(MultiOperatorProcessor.java:88)
	at io.smallrye.mutiny.operators.multi.MultiOperatorProcessor.failAndCancel(MultiOperatorProcessor.java:42)
	at io.smallrye.mutiny.operators.multi.MultiOnItemInvoke$MultiOnItemInvokeProcessor.onItem(MultiOnItemInvoke.java:38)
	at io.smallrye.mutiny.operators.multi.builders.IntervalMulti$IntervalRunnable.run(IntervalMulti.java:85)
	at org.jboss.threads.EnhancedQueueExecutor$FixedRateRunnableScheduledFuture.performTask(EnhancedQueueExecutor.java:2974)
	at org.jboss.threads.EnhancedQueueExecutor$FixedRateRunnableScheduledFuture.performTask(EnhancedQueueExecutor.java:2960)
	at org.jboss.threads.EnhancedQueueExecutor$AbstractScheduledFuture.run(EnhancedQueueExecutor.java:2742)
	at org.jboss.threads.EnhancedQueueExecutor$RepeatingScheduledFuture.run(EnhancedQueueExecutor.java:2933)
	at io.quarkus.vertx.core.runtime.VertxCoreRecorder$14.runWith(VertxCoreRecorder.java:587)
	at org.jboss.threads.EnhancedQueueExecutor$Task.run(EnhancedQueueExecutor.java:2513)
	at org.jboss.threads.EnhancedQueueExecutor$ThreadBody.run(EnhancedQueueExecutor.java:1538)
	at org.jboss.threads.DelegatingRunnable.run(DelegatingRunnable.java:29)
	at org.jboss.threads.ThreadLocalResettingRunnable.run(ThreadLocalResettingRunnable.java:29)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)
	Suppressed: org.opentest4j.AssertionFailedError
		at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:34)
		at org.junit.jupiter.api.Assertions.fail(Assertions.java:119)
		at si.puci.GreetingResourceTest.lambda$testHelloEndpoint$1(GreetingResourceTest.java:36)
		at io.smallrye.mutiny.subscription.Subscribers$CallbackBasedSubscriber.onFailure(Subscribers.java:93)
		... 15 more
Caused by: java.lang.AssertionError: 1 expectation failed.
Expected status code <200> but was <500>.

	at java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance(DirectConstructorHandleAccessor.java:62)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:502)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:486)
	at org.codehaus.groovy.reflection.CachedConstructor.invoke(CachedConstructor.java:73)
	at org.codehaus.groovy.runtime.callsite.ConstructorSite$ConstructorSiteNoUnwrapNoCoerce.callConstructor(ConstructorSite.java:108)
	at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCallConstructor(CallSiteArray.java:57)
	at org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java:263)
	at org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java:277)
	at io.restassured.internal.ResponseSpecificationImpl$HamcrestAssertionClosure.validate(ResponseSpecificationImpl.groovy:512)
	at io.restassured.internal.ResponseSpecificationImpl$HamcrestAssertionClosure$validate$1.call(Unknown Source)
	at io.restassured.internal.ResponseSpecificationImpl.validateResponseIfRequired(ResponseSpecificationImpl.groovy:696)
	at io.restassured.internal.ResponseSpecificationImpl.this$2$validateResponseIfRequired(ResponseSpecificationImpl.groovy)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at org.codehaus.groovy.runtime.callsite.PlainObjectMetaMethodSite.doInvoke(PlainObjectMetaMethodSite.java:43)
	at org.codehaus.groovy.runtime.callsite.PogoMetaMethodSite$PogoCachedMethodSiteNoUnwrapNoCoerce.invoke(PogoMetaMethodSite.java:198)
	at org.codehaus.groovy.runtime.callsite.PogoMetaMethodSite.callCurrent(PogoMetaMethodSite.java:62)
	at org.codehaus.groovy.runtime.callsite.AbstractCallSite.callCurrent(AbstractCallSite.java:185)
	at io.restassured.internal.ResponseSpecificationImpl.statusCode(ResponseSpecificationImpl.groovy:135)
	at io.restassured.specification.ResponseSpecification$statusCode$0.callCurrent(Unknown Source)
	at io.restassured.internal.ResponseSpecificationImpl.statusCode(ResponseSpecificationImpl.groovy:143)
	at io.restassured.internal.ValidatableResponseOptionsImpl.statusCode(ValidatableResponseOptionsImpl.java:89)
	at si.puci.GreetingResourceTest.lambda$testHelloEndpoint$0(GreetingResourceTest.java:29)
	at io.smallrye.context.impl.wrappers.SlowContextualConsumer.accept(SlowContextualConsumer.java:21)
	at io.smallrye.mutiny.operators.multi.MultiOnItemInvoke$MultiOnItemInvokeProcessor.onItem(MultiOnItemInvoke.java:36)
	... 12 more
```