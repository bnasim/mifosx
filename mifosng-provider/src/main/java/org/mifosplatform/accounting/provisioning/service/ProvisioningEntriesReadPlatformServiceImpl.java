/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.accounting.provisioning.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.mifosplatform.accounting.provisioning.data.LoanProductProvisioningEntryData;
import org.mifosplatform.accounting.provisioning.data.ProvisioningEntryData;
import org.mifosplatform.infrastructure.core.service.RoutingDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningEntriesReadPlatformServiceImpl implements ProvisioningEntriesReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProvisioningEntriesReadPlatformServiceImpl(final RoutingDataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Collection<LoanProductProvisioningEntryData> retrieveLoanProductsProvisioningData(Date date) {
        String formattedDate = new SimpleDateFormat("yyyy-MM-dd").format(date);
        formattedDate = "'" + formattedDate + "'";
        LoanProductProvisioningEntryMapper mapper = new LoanProductProvisioningEntryMapper(formattedDate);
        final String sql = mapper.schema();
        return this.jdbcTemplate.query(sql, mapper, new Object[] {});
    }

    private static final class LoanProductProvisioningEntryMapper implements RowMapper<LoanProductProvisioningEntryData> {

        private final StringBuilder sqlQuery;

        protected LoanProductProvisioningEntryMapper(String formattedDate) {
            sqlQuery = new StringBuilder()
                    .append("select mclient.office_id, pcd.criteria_id as criteriaid, loan.product_id,loan.currency_code,")
                    .append("GREATEST(datediff(")
                    .append(formattedDate)
                    .append(",sch.duedate),0) as numberofdaysoverdue,sch.duedate, pcd.category_id, pcd.provision_percentage,")
                    .append("loan.total_outstanding_derived as outstandingbalance, pcd.liability_account, pcd.expense_account from m_loan_repayment_schedule sch")
                    .append(" LEFT JOIN m_loan loan on sch.loan_id = loan.id")
                    .append(" LEFT JOIN m_loanproduct_provisioning_mapping lpm on lpm.product_id = loan.product_id")
                    .append(" LEFT JOIN m_provisioning_criteria_definition pcd on pcd.criteria_id = lpm.criteria_id and ")
                    .append("(pcd.min_age <= GREATEST(datediff(").append(formattedDate).append(",sch.duedate),0) and ")
                    .append("GREATEST(datediff(").append(formattedDate).append(",sch.duedate),0) <= pcd.max_age) ")
                    .append("LEFT JOIN m_client mclient ON mclient.id = loan.client_id ")
                    .append("where loan.loan_status_id=300 and sch.completed_derived=false and provision_percentage is not null ")
                    .append("GROUP BY loan.id  order by loan.product_id");
        }

        @Override
        @SuppressWarnings("unused")
        public LoanProductProvisioningEntryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long officeId = rs.getLong("office_id");
            Long productId = rs.getLong("product_id");
            String currentcyCode = rs.getString("currency_code");
            Long overdueDays = rs.getLong("numberofdaysoverdue");
            Long categoryId = rs.getLong("category_id");
            BigDecimal percentage = rs.getBigDecimal("provision_percentage");
            BigDecimal outstandingBalance = rs.getBigDecimal("outstandingbalance");
            Long liabilityAccountCode = rs.getLong("liability_account");
            Long expenseAccountCode = rs.getLong("expense_account");
            Long criteriaId = rs.getLong("criteriaid") ;
            Long historyId = null;
            
            return new LoanProductProvisioningEntryData(historyId, officeId, currentcyCode, productId, categoryId, overdueDays, percentage,
                    outstandingBalance, liabilityAccountCode, expenseAccountCode, criteriaId);
        }

        public String schema() {
            return sqlQuery.toString();
        }
    }

    @Override
    public ProvisioningEntryData retrieveProvisioningEntryData(Long entryId) {
        LoanProductProvisioningEntryRowMapper mapper = new LoanProductProvisioningEntryRowMapper();
        final String sql = mapper.getSchema() + " where entry.history_id = ?";
        Collection<LoanProductProvisioningEntryData> entries = this.jdbcTemplate.query(sql, mapper, new Object[] { entryId });
        ProvisioningEntryDataMapper mapper1 = new ProvisioningEntryDataMapper() ;
        final String sql1 = mapper1.getSchema() + " where entry.id = ?";
        ProvisioningEntryData data= this.jdbcTemplate.queryForObject(sql1, mapper1, new Object[] { entryId });
        data.setEntries(entries) ;
        return data ;
    }

    private static final class ProvisioningEntryDataMapper implements RowMapper<ProvisioningEntryData> {
        private final StringBuilder sqlQuery = new StringBuilder()
        .append("select entry.id, journal_entry_created, createdby_id, created_date, created.username as createduser,")
        .append("lastmodifiedby_id, modified.username as modifieduser, lastmodified_date ")
        .append("from m_provisioning_history entry ")
        .append("left JOIN m_appuser created ON created.id = entry.createdby_id ")
        .append("left JOIN m_appuser modified ON modified.id = entry.lastmodifiedby_id ") ;

        @Override
        @SuppressWarnings("unused")
        public ProvisioningEntryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long id = rs.getLong("id") ;
            Boolean journalEntry = rs.getBoolean("journal_entry_created") ;
            Long createdById = rs.getLong("createdby_id") ;
            String createdUser = rs.getString("createduser") ;
            Date createdDate = rs.getDate("created_date") ;
            Long modifiedById = rs.getLong("lastmodifiedby_id") ;
            String modifieUser = rs.getString("modifieduser") ;
            return new ProvisioningEntryData(id, journalEntry, createdById, createdUser, createdDate, modifiedById, modifieUser);
        }
        
        public String getSchema() {
            return sqlQuery.toString() ;
        }
        
    }
    private static final class LoanProductProvisioningEntryRowMapper implements RowMapper<LoanProductProvisioningEntryData> {

        private final StringBuilder sqlQuery = new StringBuilder()
        .append("select entry.id, entry.history_id as historyId, office_id, entry.criteria_id as criteriaid, office.name as officename, product.name as productname, entry.product_id, ")
        .append("category_id, category.category_name, liability.id as liabilityid, liability.gl_code as liabilitycode, ")
        .append("expense.id as expenseid, expense.gl_code as expensecode, entry.currency_code, entry.overdue_in_days, entry.reseve_amount from m_loanproduct_provisioning_entry entry ")
        .append("left join m_office office ON office.id = entry.office_id ")
        .append("left join m_product_loan product ON product.id = entry.product_id ")
        .append("left join m_provision_category category ON category.id = entry.category_id ")
        .append("left join acc_gl_account liability ON liability.id = entry.liability_account ")
        .append("left join acc_gl_account expense ON expense.id = entry.expense_account ") ;
        
        @Override
        @SuppressWarnings("unused")
        public LoanProductProvisioningEntryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long historyId = rs.getLong("historyId") ;
            Long officeId = rs.getLong("office_id");
            String officeName = rs.getString("officename") ;
            Long productId = rs.getLong("product_id");
            String productName = rs.getString("productname") ;
            String currentcyCode = rs.getString("currency_code");
            Long overdueDays = rs.getLong("overdue_in_days");
            Long categoryId = rs.getLong("category_id");
            String categoryName = rs.getString("category_name") ;
            BigDecimal amountreserved = rs.getBigDecimal("reseve_amount");
            Long liabilityAccountCode = rs.getLong("liabilityid");
            String liabilityAccountName = rs.getString("liabilitycode") ;
            String expenseAccountName = rs.getString("expensecode") ;
            Long expenseAccountCode = rs.getLong("expenseid");
            Long criteriaId = rs.getLong("criteriaid") ;
            return new LoanProductProvisioningEntryData(historyId, officeId, officeName, currentcyCode, productId, productName, categoryId, categoryName, overdueDays, amountreserved,
                    liabilityAccountCode, liabilityAccountName, expenseAccountCode, expenseAccountName, criteriaId);
        }
        
        public String getSchema() {
            return sqlQuery.toString() ;
        }
    }
    
    @Override
    public Collection<ProvisioningEntryData> retrieveAllProvisioningEntries() {
        ProvisioningEntryDataMapper mapper = new ProvisioningEntryDataMapper() ;
        final String sql1 = mapper.getSchema() + " order by entry.created_date ";
        return this.jdbcTemplate.query(sql1, mapper, new Object[] {});
    }
    
    @Override
    public ProvisioningEntryData retrieveProvisioningEntryData(String date) {
        ProvisioningEntryDataMapper mapper1 = new ProvisioningEntryDataMapper() ;
        final String sql1 = mapper1.getSchema() + " where entry.created_date like " + "'"+date+"%'" ;
        ProvisioningEntryData data = null ;
        try {
            data= this.jdbcTemplate.queryForObject(sql1, mapper1, new Object[] {});    
        }catch(EmptyResultDataAccessException e) {
            
        }
        
        return data ;
    }
    
    @Override
    public ProvisioningEntryData retrieveProvisioningEntryDataByCriteriaId(Long criteriaId) {
        ProvisioningEntryData data = null ;
        LoanProductProvisioningEntryRowMapper mapper = new LoanProductProvisioningEntryRowMapper();
        final String sql = mapper.getSchema() + " where entry.criteria_id = ?";
        Collection<LoanProductProvisioningEntryData> entries = this.jdbcTemplate.query(sql, mapper, new Object[] { criteriaId });
        if(entries != null && entries.size() > 0) {
            Long entryId = ((LoanProductProvisioningEntryData)entries.toArray()[0]).getHistoryId() ;
            ProvisioningEntryDataMapper mapper1 = new ProvisioningEntryDataMapper() ;
            final String sql1 = mapper1.getSchema() + " where entry.id = ?";
            data= this.jdbcTemplate.queryForObject(sql1, mapper1, new Object[] { entryId });
            data.setEntries(entries) ;    
        }
        return data ;
    }

    @Override
    public ProvisioningEntryData retrieveExistingProvisioningIdDateWithJournals() {
        ProvisioningEntryData data = null ;
        ProvisioningEntryIdDateRowMapper mapper = new ProvisioningEntryIdDateRowMapper() ;
        try {
            data = this.jdbcTemplate.queryForObject(mapper.schema(), mapper, new Object[] {});    
        }catch(EmptyResultDataAccessException e) {
            data = null ;
        }
        return data ;
    }
 
    private static final class ProvisioningEntryIdDateRowMapper implements RowMapper<ProvisioningEntryData> {

        StringBuffer buff = new StringBuffer()
        .append("select history1.id, history1.created_date from m_provisioning_history history1 ")
        .append("where history1.created_date = (select max(history2.created_date) from m_provisioning_history history2 ")
        .append("where history2.journal_entry_created='1')");
        
        @Override
        @SuppressWarnings("unused")
        public ProvisioningEntryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> map = new HashMap<>() ;
            Long id = rs.getLong("id") ;
            Date createdDate = rs.getDate("created_date") ;
            Long createdBy = null ;
            String createdName = null ;
            Long modifiedBy = null ;
            String modifiedName = null ; 
            return new ProvisioningEntryData(id, Boolean.TRUE, createdBy, createdName, createdDate, modifiedBy, modifiedName) ;  
        }
        
        public String schema() {        
            return buff.toString() ;
        }
    }
    
}