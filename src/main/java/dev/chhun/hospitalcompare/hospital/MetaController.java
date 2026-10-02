package dev.chhun.hospitalcompare.hospital;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "메타")
@RestController
@RequestMapping("/api/meta")
class MetaController {

	private final HospitalQueryService hospitalQueryService;

	MetaController(HospitalQueryService hospitalQueryService) {
		this.hospitalQueryService = hospitalQueryService;
	}

	@Operation(summary = "active 스냅샷 기준일과 건수", description = "화면에 표시할 데이터 기준일. active 스냅샷이 없으면 404.")
	@GetMapping("/snapshot")
	SnapshotResponse snapshot() {
		return hospitalQueryService.activeSnapshot()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "active 스냅샷이 없습니다"));
	}

}
