package com.example.demo.bean.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class LawCaseBatchInfoStateVo implements Serializable {

    private static final long serialVersionUID = 1L;

    //批次ID
    private String batchId;
    //批次名称
    private String batchName;
    //批次状态
    private String state;

}
