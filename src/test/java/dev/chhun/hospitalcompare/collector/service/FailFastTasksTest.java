package dev.chhun.hospitalcompare.collector.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FailFastTasksTest {

	@Test
	void 동시에_도는_작업은_스레드_수를_넘지_않고_모두_끝난다() {
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		AtomicInteger finished = new AtomicInteger();
		List<Callable<Void>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> {
				maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
				Thread.sleep(50);
				running.decrementAndGet();
				finished.incrementAndGet();
				return null;
			});
		}

		FailFastTasks.runAll(tasks, 2);

		assertThat(maxRunning).hasValue(2);
		assertThat(finished).hasValue(10);
	}

}
