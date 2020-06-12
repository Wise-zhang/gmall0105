package com.atguigu.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.PmsSkuAttrValue;
import com.atguigu.gmall.bean.PmsSkuImage;
import com.atguigu.gmall.bean.PmsSkuInfo;
import com.atguigu.gmall.bean.PmsSkuSaleAttrValue;
import com.atguigu.gmall.manage.mapper.PmsSkuAttrValueMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuImageMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuInfoMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuSaleAttrValueMapper;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    private PmsSkuInfoMapper pmsSkuInfoMapper;
    @Autowired
    private PmsSkuAttrValueMapper pmsSkuAttrValueMapper;
    @Autowired
    private PmsSkuImageMapper pmsSkuImageMapper;
    @Autowired
    private PmsSkuSaleAttrValueMapper pmsSkuSaleAttrValueMapper;
    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void saveSkuInfo(PmsSkuInfo pmsSkuInfo) {

        List<PmsSkuAttrValue> skuAttrValueList = pmsSkuInfo.getSkuAttrValueList();
        List<PmsSkuImage> skuImageList = pmsSkuInfo.getSkuImageList();
        List<PmsSkuSaleAttrValue> skuSaleAttrValueList = pmsSkuInfo.getSkuSaleAttrValueList();
        // 插入商品信息
        pmsSkuInfoMapper.insertSelective(pmsSkuInfo);
        String pmsSkuInfoId = pmsSkuInfo.getId();
        for (PmsSkuAttrValue pmsSkuAttrValue : skuAttrValueList) {
            pmsSkuAttrValue.setSkuId(pmsSkuInfoId);
            pmsSkuAttrValueMapper.insertSelective(pmsSkuAttrValue);
        }
        for (PmsSkuImage pmsSkuImage : skuImageList) {
            // 处理productImgId
            pmsSkuImage.setProductImgId(pmsSkuImage.getSpuImgId());

            pmsSkuImage.setSkuId(pmsSkuInfoId);
            pmsSkuImageMapper.insertSelective(pmsSkuImage);
        }
        for (PmsSkuSaleAttrValue pmsSkuSaleAttrValue : skuSaleAttrValueList) {
            pmsSkuSaleAttrValue.setSkuId(pmsSkuInfoId);
            pmsSkuSaleAttrValueMapper.insertSelective(pmsSkuSaleAttrValue);
        }
    }

    private PmsSkuInfo getSkuByIdFromDb(String skuId) {
        // sku商品对象
        PmsSkuInfo skuInfo = new PmsSkuInfo();
        skuInfo.setId(skuId);
        PmsSkuInfo pmsSkuInfo = pmsSkuInfoMapper.selectOne(skuInfo);

        // sku的图片集合
        PmsSkuImage pmsSkuImage = new PmsSkuImage();
        pmsSkuImage.setSkuId(skuId);
        List<PmsSkuImage> pmsSkuImages = pmsSkuImageMapper.select(pmsSkuImage);
        pmsSkuInfo.setSkuImageList(pmsSkuImages);
        return pmsSkuInfo;
    }

    @Override
    public PmsSkuInfo getSkuById(String skuId) {
        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();
        // 链接缓存
        Jedis jedis = redisUtil.getJedis();
        // 查询缓存
        String skuKey = "sku:" + skuId + ":info";
        String skuJson = jedis.get(skuKey);

        if (StringUtils.isNotBlank(skuJson)) {
            pmsSkuInfo = JSON.parseObject(skuJson, PmsSkuInfo.class);
        } else {
            // 如果缓存中没有，查询MySQL
            // 设置分布式锁
            String token = UUID.randomUUID().toString();
            String OK = jedis.set("sku:" + skuId + ":lock", token, "nx", "px", 10 * 1000 * 60);
            if (StringUtils.isNotBlank(OK) && OK.equals("OK")) {
                // 设置成功就有权在10秒的过期时间内访问数据库
                pmsSkuInfo = getSkuByIdFromDb(skuId);
                if (pmsSkuInfo != null) {
                    // 将MySQL查询结果存入到redis
                    jedis.set("sku:" + skuId + ":info", JSON.toJSONString(pmsSkuInfo));
                } else {
                    // 数据库中不存在改sku
                    // 为了防止缓存穿透将null或者空字符串设置给redis
                    jedis.setex("sku:" + skuId + ":info", 60 * 3, JSON.toJSONString(""));
                }
                // 在访问MySQL后，将MySQL的分布式锁释放
                String lockToken = jedis.get("sku:" + skuId + ":lock");
                if (StringUtils.isNotBlank(lockToken) && lockToken.equals(token)) {
                    // jedis.eval("lua");可以用lua脚本，在查询到key的同事删除该key，防止高并发下的意外
//                     jedis.del("sku:" + skuId + ":lock");
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    jedis.eval(script, Collections.singletonList("lock"), Collections.singletonList(token));
                }
            } else {
                // 设置失败就自旋（该线程在睡眠几秒后重新尝试访问本方法）
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return getSkuById(skuId);
            }
        }
        jedis.close();
        return pmsSkuInfo;
    }

    @Override
    public List<PmsSkuInfo> selectSkuSaleAttrValueListBySpu(String productId) {
        return pmsSkuInfoMapper.selectSkuSaleAttrValueListBySpu(productId);
    }

    @Override
    public List<PmsSkuInfo> getAllSku() {
        List<PmsSkuInfo> pmsSkuInfos = pmsSkuInfoMapper.selectAll();
        for (PmsSkuInfo pmsSkuInfo : pmsSkuInfos) {
            String skuInfoId = pmsSkuInfo.getId();
            PmsSkuAttrValue pmsSkuAttrValue = new PmsSkuAttrValue();
            pmsSkuAttrValue.setSkuId(skuInfoId);
            List<PmsSkuAttrValue> select = pmsSkuAttrValueMapper.select(pmsSkuAttrValue);
            pmsSkuInfo.setSkuAttrValueList(select);
        }
        return pmsSkuInfos;
    }

    @Override
    public boolean checkPrice(String productSkuId, BigDecimal price) {
        boolean b = false;
        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();
        pmsSkuInfo.setId(productSkuId);
        PmsSkuInfo pmsSkuInfo1 = pmsSkuInfoMapper.selectOne(pmsSkuInfo);
        BigDecimal price1 = pmsSkuInfo1.getPrice();
        if (price.compareTo(price1) == 0) {
            b = true;
        }

        return b;
    }
}
