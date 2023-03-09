# Page分页查询

## Demo
参考：`com.faber.api.base.rbac.biz.pageVo`
```java
public TableRet<RbacUserRoleRetVo> pageVo(BasePageQuery<RbacUserRoleQueryVo> query) {
    PageInfo<RbacUserRoleRetVo> info = PageHelper.startPage(query.getCurrent(), query.getPageSize())
            .doSelectPageInfo(() -> baseMapper.pageVo(query.getQuery(), query.getSorter()));
    return new TableRet<>(info);
}
```