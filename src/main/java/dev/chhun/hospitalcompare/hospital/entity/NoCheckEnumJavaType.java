package dev.chhun.hospitalcompare.hospital.entity;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * enum을 이름으로 VARCHAR에 저장하되, 값 목록 CHECK 제약은 만들지 않는 Hibernate 타입.
 * Hibernate는 enum 컬럼마다 CHECK (col IN (...))를 만들고 ddl-auto=update는 기존 제약을 고치지 않는다.
 * 그러면 enum 값을 추가한 뒤 기존 DB에서 INSERT가 실패한다. AttributeConverter를 써도 Hibernate가 변환기를 거쳐
 * enum 값마다 CHECK를 만들기 때문에, CHECK를 만드는 이 한 메서드만 끈다(EnumColumnSchemaTest가 지킨다).
 */
abstract class NoCheckEnumJavaType<E extends Enum<E>> extends EnumJavaType<E> {

	NoCheckEnumJavaType(Class<E> enumClass) {
		super(enumClass);
	}

	@Override
	public String getCheckCondition(String columnName, JdbcType jdbcType, BasicValueConverter<E, ?> converter,
			Dialect dialect) {
		return null;
	}

}
