package com.atguigu.gmall.item.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.PmsProductSaleAttr;
import com.atguigu.gmall.bean.PmsSkuInfo;
import com.atguigu.gmall.bean.PmsSkuSaleAttrValue;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.service.SpuService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@CrossOrigin
public class ItemController {
    @Reference
    private SkuService skuService;
    @Reference
    private SpuService spuService;

    @RequestMapping("{skuId}.html")
    public String item(@PathVariable String skuId, ModelMap map) {
        PmsSkuInfo pmsSkuInfo = skuService.getSkuById(skuId);
        //sku对象
        map.put("skuInfo", pmsSkuInfo);
        // 销售属性列表
        List<PmsProductSaleAttr> pmsProductSaleAttrs = spuService.spuSaleAttrListCheckBySku(pmsSkuInfo.getProductId(), pmsSkuInfo.getId());
        map.put("spuSaleAttrListCheckBySku", pmsProductSaleAttrs);
        // 查询当前sku的spu的其他sku的集合的hash表
        Map<String, String> skuSaleAttrHash = new HashMap<>();
        List<PmsSkuInfo> pmsSkuInfos = skuService.selectSkuSaleAttrValueListBySpu(pmsSkuInfo.getProductId());
        for (PmsSkuInfo skuInfo : pmsSkuInfos) {
            StringBuilder k = new StringBuilder();
            String v = skuInfo.getId();
            List<PmsSkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
            for (PmsSkuSaleAttrValue pmsSkuSaleAttrValue : skuSaleAttrValueList) {
                k.append(pmsSkuSaleAttrValue.getSaleAttrValueId()).append("|");
            }
            skuSaleAttrHash.put(k.toString(), v);
        }
        // 将sku的销售属性hash表放到页面
        String skuSaleAttrHashJsonStr = JSON.toJSONString(skuSaleAttrHash);
        map.put("skuSaleAttrHashJsonStr", skuSaleAttrHashJsonStr);
        return "item";
    }
}
