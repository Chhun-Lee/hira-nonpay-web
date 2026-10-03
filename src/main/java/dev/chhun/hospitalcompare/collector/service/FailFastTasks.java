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

/**
 * 작업들을 가상 스레드로 동시에 돌리고, 하나라도 실패하면 나머지를 취소한 뒤 그 오류를 던진다.
 * 모든 작업이 실제로 끝날 때까지 돌아오지 않는다(executor.close). 그래서 실패를 기록한 뒤 늦은 적재가 끼어들지 않는다.
 * 동시 실행 수와 초당 호출은 각 작업이 거치는 HIRA 관문이 제한한다.
 */
final class FailFastTasks {

	private FailFastTasks() {
	}

	static void runAll(List<Callable<Void>> tasks) {
		if (tasks.isEmpty()) {
			return;
		}
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
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
