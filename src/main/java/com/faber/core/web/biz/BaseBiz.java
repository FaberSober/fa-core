package com.faber.core.web.biz;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faber.core.annotation.FaModalName;
import com.faber.core.config.mybatis.base.FaBaseMapper;
import com.faber.core.config.mybatis.utils.WrapperUtils;
import com.faber.core.constant.CommonConstants;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.exception.BuzzException;
import com.faber.core.service.ConfigSceneService;
import com.faber.core.utils.EasyExcelUtils;
import com.faber.core.utils.FaEnumUtils;
import com.faber.core.vo.msg.TableRet;
import com.faber.core.vo.query.ConditionGroup;
import com.faber.core.vo.query.QueryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.URLEncoder;
import java.util.*;

/**
 * 业务Service父类
 * 1. 实现MyBatis的一些通用查询方法
 * <p>
 * Version 1.0.0
 */
public abstract class BaseBiz<M extends FaBaseMapper<T>, T> extends ServiceImpl<M, T> {

    protected final Logger _logger = LoggerFactory.getLogger(this.getClass());

    private ConfigSceneService configSceneService;

    /**
     * 在save、updateById之前对bean做一些操作
     * @param entity
     */
    protected void saveBefore(T entity) {
    }

    @Override
    public boolean save(T entity) {
        saveBefore(entity);
        return super.save(entity);
    }

    @Override
    public boolean saveBatch(Collection<T> entityList) {
        for (T entity : entityList) {
            saveBefore(entity);
        }
        return super.saveBatch(entityList);
    }

    @Override
    public boolean updateById(T entity) {
        saveBefore(entity);
        return super.updateById(entity);
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
        long count = super.count(wrapper);
        if (count > CommonConstants.QUERY_MAX_COUNT) {
            throw new BuzzException("单次查询列表返回数据不可超过" + CommonConstants.QUERY_MAX_COUNT);
        }
        List<T> list = super.list(wrapper);
        this.decorateList(list);
        return list;
    }

    /**
     * 根据组合查询条件，下载Excel
     *
     * @param query
     * @throws IOException
     */
    public void exportExcel(QueryParams query) throws IOException {
        List<T> list = this.list(query);
        Class<T> clazz = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.sendFileExcel(clazz, list);
    }

    /**
     * response写入下载Excel文件流
     *
     * @param clazz
     * @param list
     * @throws IOException
     */
    protected void sendFileExcel(Class<T> clazz, List<T> list) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();

        FaModalName anno = clazz.getAnnotation(FaModalName.class);

        String fileName = DateUtil.now() + "";
        if (anno != null) {
            fileName = anno.name() + "_" + fileName;
        }

        // 这里注意 有同学反应使用swagger 会导致各种问题，请直接用浏览器或者用postman
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        // 这里URLEncoder.encode可以防止中文乱码 当然和easyexcel没有关系
        fileName = URLEncoder.encode(fileName, "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        response.setHeader("fa-filename", fileName + ".xlsx");

        EasyExcel
                .write(response.getOutputStream(), clazz)
                .registerWriteHandler(EasyExcelUtils.genHeaderWriteStyle())
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
//                .registerConverter(new BaseEnumConverter())
//                .registerConverter(new LocalDateTimeConverter())
                .sheet("Sheet1")
                .doWrite(list);
    }

    public T getByIdWithCache(Serializable id) {
        Map<Serializable, T> cache = BaseContextHandler.getCacheMap(getEntityClass());
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        T user = super.getById(id);
        cache.put(id, user);
        return user;
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

    public String updateValueToStr(Field field, Object value) {
        if (value == null) return "";
        if (IEnum.class.isAssignableFrom(field.getType())) {
            return (String) ReflectUtil.getFieldValue(value, "desc");
        }
        if (value instanceof Date) return DateUtil.formatDateTime((Date) value);
        return StrUtil.toString(value);
    }

}
