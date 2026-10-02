package dev.chhun.hospitalcompare;

import org.springframework.boot.SpringApplication;

public class TestHospitalCompareApplication {

	public static void main(String[] args) {
		SpringApplication.from(HospitalCompareApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
