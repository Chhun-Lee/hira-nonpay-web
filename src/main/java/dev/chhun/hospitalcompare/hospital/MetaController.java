package dev.chhun.hospitalcompare.hospital;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/meta")
class MetaController {

	private final HospitalQueryService hospitalQueryService;

	MetaController(HospitalQueryService hospitalQueryService) {
		this.hospitalQueryService = hospitalQueryService;
	}

	/** 화면에 표시할 active 스냅샷의 기준일과 건수 */
	@GetMapping("/snapshot")
	SnapshotResponse snapshot() {
		return hospitalQueryService.activeSnapshot()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "active 스냅샷이 없습니다"));
	}

}
