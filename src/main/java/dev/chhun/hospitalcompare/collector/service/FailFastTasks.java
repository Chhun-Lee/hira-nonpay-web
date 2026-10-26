package dev.chhun.hospitalcompare.collector.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * 작업들을 고정 크기 스레드 풀로 동시에 돌리고, 하나라도 실패하면 나머지를 취소한 뒤 그 오류를 던진다.
 * 모든 작업이 실제로 끝날 때까지 돌아오지 않는다(executor.close). 그래서 실패를 기록한 뒤 늦은 적재가 끼어들지 않는다.
 * 풀 크기는 관문의 동시 실행 수와 같게 넘긴다. 동시 실행 수가 4~8이라 작업마다 가상 스레드를 둘 이유가 없다.
 * 재시도 백오프 중인 작업도 풀 스레드를 쥐고 있어 그동안 실제 동시성은 풀 크기보다 작아진다(감수한다).
 */
final class FailFastTasks {

	private FailFastTasks() {
	}

	/**
	 * @param threads 풀 크기(동시에 도는 작업 수의 상한)
	 */
	static void runAll(List<Callable<Void>> tasks, int threads) {
		if (tasks.isEmpty()) {
			return;
		}
		// 데몬 스레드라 강제 종료 때 응답 대기 중인 작업이 JVM 종료를 붙잡지 않는다(가상 스레드 때와 같다).
		ThreadFactory factory = Thread.ofPlatform().name("collector-", 1).daemon(true).factory();
		try (ExecutorService executor = Executors.newFixedThreadPool(threads, factory)) {
			CompletionService<Void> completion = new ExecutorCompletionService<>(executor);
			List<Future<Void>> futures = new ArrayList<>(tasks.size());
			for (Callable<Void> task : tasks) {
				futures.add(completion.submit(task));
			}
			try {
				for (int done = 0; done < futures.size(); done++) {
					completion.take().get();
				}
			} catch (ExecutionException e) {
				futures.forEach(future -> future.cancel(true));
				throw e.getCause() instanceof RuntimeException runtime
						? runtime
						: new IllegalStateException(e.getCause());
			} catch (InterruptedException e) {
				futures.forEach(future -> future.cancel(true));
				Thread.currentThread().interrupt();
				throw new IllegalStateException("수집이 중단되었습니다");
			}
		}
	}

}
