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
        /** 小程序采集端不再采集详细位置，允许为空；管理端表单仍自行校验必填 */
        public String detailLocation;
        public BigDecimal latitude;
        public BigDecimal longitude;
        public BigDecimal locationAccuracyMeters;
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
        /** 资源 id 取自 URL 路径：控制器在参数绑定后才回填该字段，因此这里不能加 @NotNull（校验先于赋值执行，会把所有更新请求拦下） */
        public Long id;
    }

    public static class ComponentInput {
        @NotBlank(message = "部件编号不能为空") public String itemCode;
        public LocalDate manufactureDate;
    }

    public static class Component {
        public String itemCode;
        public String itemName;
        public LocalDate manufactureDate;
        /** 采集员登记该部件的时间 */
        public java.time.LocalDateTime createdAt;
        /** 该部件的保养周期（月），来自巡检项配置，未配置为 null */
        public Integer maintenanceCycleMonths;
        /** 该部件的下次保养时间＝生产日期＋保养周期，未配置周期或无生产日期为 null */
        public LocalDate nextMaintenanceAt;
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
        public BigDecimal locationAccuracyMeters;
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
        /** 采集员建档登记的部件数量（列表页直接展示，明细经详情接口获取） */
        public Integer componentCount;
        /** 采集员拍摄的设施初始照片数量（列表页直接展示，照片清单经照片接口获取） */
        public Integer photoCount;
        public List<Component> components;
    }
}
