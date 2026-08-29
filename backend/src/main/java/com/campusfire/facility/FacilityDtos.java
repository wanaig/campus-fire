package com.campusfire.facility;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class FacilityDtos {
    private FacilityDtos() {}

    public static class CreateRequest {
        @NotBlank(message = "请输入设施编号") public String facilityNo;
        @NotBlank(message = "请选择设施类型") public String facilityType;
        @NotBlank(message = "请输入设施名称") public String name;
        @NotBlank(message = "请输入校区") public String campus;
        @NotBlank(message = "请输入楼栋") public String building;
        @NotBlank(message = "请输入楼层") public String floor;
        @NotBlank(message = "请输入区域") public String area;
        @NotBlank(message = "请输入详细位置") public String detailLocation;
        public BigDecimal latitude;
        public BigDecimal longitude;
        public String qrToken;
        public String brand;
        public String model;
        public String specification;
        public LocalDate manufactureDate;
        public LocalDate commissionedDate;
        public String lifecycleStatus = "IN_USE";
    }

    public static class UpdateRequest extends CreateRequest {
        @NotNull(message = "设施编号不能为空") public Long id;
    }

    public static class Summary {
        public Long id;
        public String facilityNo;
        public String facilityType;
        public String name;
        public String campus;
        public String building;
        public String floor;
        public String area;
        public String detailLocation;
        public BigDecimal latitude;
        public BigDecimal longitude;
        public String qrToken;
        public String brand;
        public String model;
        public String specification;
        public LocalDate manufactureDate;
        public LocalDate commissionedDate;
        public String lifecycleStatus;
        public Long updateRuleId;
        public LocalDate expectedUpdateDate;
        public java.time.LocalDateTime nextMaintenanceAt;
    }
}
