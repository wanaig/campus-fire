package com.campusfire.facility;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FacilityDtos {
    private FacilityDtos() {}

    public static class CreateRequest {
        /** 系统唯一编号：由后端自动生成，前端仅做回传不可修改；管理端仍允许显式指定 */
        public String facilityNo;
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
        /** 采集员按位置勾选的部件及各自生产日期；null 表示未提供（保持原值），空数组表示清空 */
        public List<ComponentInput> components;
    }

    public static class UpdateRequest extends CreateRequest {
        @NotNull(message = "设施编号不能为空") public Long id;
    }

    public static class ComponentInput {
        @NotBlank(message = "部件编号不能为空") public String itemCode;
        public LocalDate manufactureDate;
    }

    public static class Component {
        public String itemCode;
        public String itemName;
        public LocalDate manufactureDate;
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
        public List<Component> components;
    }
}
