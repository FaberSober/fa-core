package com.faber.core.web.biz;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faber.core.config.mybatis.base.FaBaseMapper;
import com.faber.core.config.mybatis.utils.WrapperUtils;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.exception.BuzzException;
import com.faber.core.service.ConfigSceneService;
import com.faber.core.service.StorageService;
import com.faber.core.utils.FaEnumUtils;
import com.faber.core.utils.FaExcelUtils;
import com.faber.core.vo.excel.CommonImportExcelReqVo;
import com.faber.core.vo.msg.TableRet;
import com.faber.core.vo.query.ConditionGroup;
import com.faber.core.vo.query.QueryParams;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 业务Service父类
 * 1. 实现MyBatis的一些通用查询方法
 * <p>
 * Version 1.0.0
 */
public abstract class BaseBiz<M extends FaBaseMapper<T>, T> extends ServiceImpl<M, T> {

    protected final Logger _logger = LoggerFactory.getLogger(this.getClass());
    protected final int DEFAULT_PAGE_SIZE = 1000;

    private ConfigSceneService configSceneService;


    /**
     * 在save、update之后做一些同步数据操作
     *
     * @param entity
     */
    protected void afterChange(T entity) {
    }

    /**
     * 在save、updateById之前对bean做一些操作
     *
     * @param entity
     */
    protected void saveBefore(T entity) {
    }

    /**
     * 在save之后对bean做一些操作
     *
     * @param entity
     */
    protected void afterSave(T entity) {
    }

    @Override
    public boolean save(T entity) {
        saveBefore(entity);
        boolean flag = super.save(entity);
        afterSave(entity);
        afterChange(entity);
        return flag;
    }

    @Override
    public boolean saveBatch(Collection<T> entityList) {
        if (entityList == null || entityList.isEmpty()) return true;
        for (T entity : entityList) {
            saveBefore(entity);
        }
        return super.saveBatch(entityList);
    }

    /**
     * 在updateById之后对bean做一些操作
     *
     * @param entity
     */
    protected void afterUpdate(T entity) {
    }

    @Override
    public boolean updateById(T entity) {
        saveBefore(entity);
        boolean flag = super.updateById(entity);
        afterUpdate(entity);
        afterChange(entity);
        return flag;
    }

    /**
     * 在removeById之后对bean做一些操作
     *
     * @param id
     */
    protected void afterRemove(Serializable id) {
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean flag = super.removeById(id);
        afterRemove(id);
        return flag;
    }

    public List<T> getByIds(List<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        return super.list(wrapper);
    }

    public List<T> mineList(QueryParams query) {
        query.getQuery().put("crtUser", getCurrentUserId());
        return this.list(query);
    }

    /**
     * {@link QueryParams}自定义预先处理
     *
     * @param query
     */
    protected void preProcessQuery(QueryParams query) {
    }

    public QueryWrapper<T> parseQuery(QueryParams query) {
        this.preProcessQuery(query);

        this.processSceneId(query);

        return WrapperUtils.parseQuery(query, getEntityClass());
    }

    /**
     * sceneId 场景ID查询-追加到条件组中
     *
     * @param query
     */
    protected void processSceneId(QueryParams query) {
        if (query.getSceneId() == null || query.getSceneId() == 0) {
            return;
        }

        if (configSceneService == null) {
            configSceneService = SpringUtil.getBean(ConfigSceneService.class);
        }
        try {
            ConditionGroup[] configData = configSceneService.getConfigDataById(query.getSceneId());
            if (configData != null) {
                query.addConditionGroupList(ListUtil.toList(configData));
            }
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
            throw new BuzzException("解析条件失败，请联系管理员");
        }
    }

    public T getDetailById(Serializable id) {
        T item = super.getById(id);
        this.decorateOne(item);
        return item;
    }

    public void decorateOne(T i) {
    }

    public void decorateList(List<T> list) {
        list.forEach(this::decorateOne);
    }

    public TableRet<T> selectPageByQuery(QueryParams query) {
        QueryWrapper<T> wrapper = parseQuery(query);
        if (query.getPageSize() > 1000) {
            throw new BuzzException("查询结果数量大于1000，请缩小查询范围");
        }

        // page query
        Page<T> page = new Page<>(query.getCurrent(), query.getPageSize());
        Page<T> result = super.page(page, wrapper);
        TableRet<T> table = new TableRet<T>(result);

        // add dict options
        this.addDictOptions(table, getEntityClass());

        // decorate
        decorateList(table.getData().getRows());

        return table;
    }

    public void addDictOptions(TableRet<?> table, Class<?> clazz) {
        Field[] fields = ReflectUtil.getFields(clazz, field -> IEnum.class.isAssignableFrom(field.getType()));
        for (Field field : fields) {
            table.getData().addDict(field.getName(), FaEnumUtils.toOptions((Class<? extends IEnum<Serializable>>) field.getType()));
        }
    }

    public List<T> list(QueryParams query) {
        QueryWrapper<T> wrapper = parseQuery(query);
        long total = super.count(wrapper);
//        if (total > CommonConstants.QUERY_MAX_COUNT) {
//            throw new BuzzException("单次查询列表返回数据不可超过" + CommonConstants.QUERY_MAX_COUNT);
//        }

        int page = (int) Math.ceil(total / (double) DEFAULT_PAGE_SIZE);

        List<T> allList = new ArrayList<>();

        for (int i = 1; i <= page; i++) {
            PageInfo<T> info = PageHelper.startPage(i, DEFAULT_PAGE_SIZE)
                    .doSelectPageInfo(() -> super.list(wrapper));
            allList.addAll(info.getList());
        }

        this.decorateList(allList);
        return allList;
    }

    /**
     * 根据组合查询条件，下载Excel
     *
     * @param query
     * @throws IOException
     */
    public void exportExcel(QueryParams query) throws IOException {
        List<T> list = this.list(query);
        FaExcelUtils.sendFileExcel(this.entityClass, list);
    }

    /**
     * 下载空Excel，只带有表头，适用于导入文件
     *
     * @throws IOException
     */
    public void exportTplExcel() throws IOException {
        FaExcelUtils.sendFileExcel(this.entityClass, Collections.emptyList());
    }

    /**
     * 在导入Excel的时候，做一些bean属性的校验、补全
     *
     * @param entity
     */
    protected void saveExcelEntity(T entity) {
        if (entity == null) return;

        TableInfo tableInfo = TableInfoHelper.getTableInfo(this.entityClass);
        Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");

        String keyProperty = tableInfo.getKeyProperty();
        Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for id from entity!");


        Object idVal = tableInfo.getPropertyValue(entity, tableInfo.getKeyProperty());
        T dbEntity = this.getById((Serializable) idVal);
        if (dbEntity == null) {
            this.save(entity);
        } else {
            this.updateById(entity);
        }
    }

    public File getFileById(String fileId) {
        StorageService storageService = SpringUtil.getBean(StorageService.class);
        return storageService.getByFileId(fileId);
    }

    public void importExcel(CommonImportExcelReqVo reqVo) {
        File file = getFileById(reqVo.getFileId());
        List<T> saveList = new ArrayList<>();
        FaExcelUtils.simpleRead(file, this.entityClass, i -> {
            saveList.add(i);
        });
        this.saveOrUpdateBatch(saveList);
    }

    /**
     * 根据ID查询实体基础信息
     *
     * @param id ID
     * @return
     */
    public T getByIdWithCache(Serializable id) {
        Map<Serializable, T> cache = BaseContextHandler.getCacheMap(getEntityClass());
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        T entity = super.getById(id);
        cache.put(id, entity);
        return entity;
    }

    /**
     * 根据ID查询实体详情
     *
     * @param id ID
     * @return
     */
    public T getDetailByIdWithCache(Serializable id) {
        Map<Serializable, T> cache = BaseContextHandler.getCacheMap(getEntityClass());
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        T entity = super.getById(id);
        decorateOne(entity);
        cache.put(id, entity);
        return entity;
    }

    public String getCurrentUserId() {
        return BaseContextHandler.getUserId();
    }

    public void removePerById(Serializable id) {
        // 用SQL进行物理删除
        baseMapper.deletePermanentById(id);
    }

    @Transactional(
            rollbackFor = {Exception.class}
    )
    public void removePerBatchByIds(List<Serializable> ids) {
        for (Serializable id : ids) {
            this.removePerById(id);
        }
    }

    public void removeByQuery(QueryParams query) {
        QueryWrapper<T> wrapper = parseQuery(query);
        super.remove(wrapper);
    }

    public String updateValueToStr(Field field, Object value) {
        if (value == null) return "";
        if (IEnum.class.isAssignableFrom(field.getType())) {
            return (String) ReflectUtil.getFieldValue(value, "desc");
        }
        if (value instanceof Date) return DateUtil.formatDateTime((Date) value);
        return StrUtil.toString(value);
    }

    /**
     * 获取最大的排序
     *
     * @param colName 取最大排序的
     * @return 最大的排序
     */
    protected Integer getMaxSort(String colName) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc(colName);
        wrapper.select(String.format("IFNULL(max(%s), -1) as value", colName));
        List<Map<String, Object>> result = baseMapper.selectMaps(wrapper);
        return Integer.parseInt(result.get(0).get("value") + "");
    }


    /**
     * 获取最大的排序
     *
     * @param colName 取最大排序的
     * @return 最大的排序
     */
    protected Integer getMaxSort(QueryWrapper<T> wrapper, String colName) {
        wrapper.orderByDesc(colName);
        wrapper.select(String.format("IFNULL(max(%s), -1) as value", colName));
        List<Map<String, Object>> result = baseMapper.selectMaps(wrapper);
        return Integer.parseInt(result.get(0).get("value") + "");
    }

    /**
     * 返回最上层一条数据，使用limit 1
     *
     * @param wrapper mybatis-plus wrapper
     * @return 最上层一条数据
     */
    protected T getTop(LambdaQueryChainWrapper<T> wrapper) {
        return wrapper.last("limit 1").one();
    }

}
