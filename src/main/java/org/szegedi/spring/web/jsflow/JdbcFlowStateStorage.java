/*
   Copyright 2006 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.szegedi.spring.web.jsflow;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage;

/**
 * <p>
 * A flow state storage that operates against a JDBC data source. The name of
 * the table and the columns are configurable, by default, it assumes one
 * autoincrementing, unique constrained, indexed column named "id", and one
 * column capable of storing a byte array (i.e. a BLOB) named "state" in a table
 * named "webflowstates". Additionally, a column named "random" is used to store
 * a nonunique but random 32-bit number. This makes it harder for attackers to
 * guess valid flowstate IDs. Note that no mechanism for purging "old" states is
 * provided. You need to write your own periodical task to delete the states
 * that are considered old. Adding a timestamp column to the table that defaults
 * to the time of insert is advised. I.e. a MySQL table definition would look
 * like
 * </p><p>
 * <tt>create table webflowstates (id bigint not null auto_increment, state blob
 * not null, random not null int, created timestamp default current_timestamp, primary key (id));
 * </tt>
 * @author Attila Szegedi
 * @version $Id$
 */
public class JdbcFlowStateStorage extends AbstractFlowStateStorage
{
    private static final char SEPARATOR = ':';
    private JdbcOperations jdbcOperations;
    private String tableName = "webflowstates";
    private String stateColumnName = "state";
    private String randomColumnName = "random";
    private String idColumnName = "id";
    private String selectQuery;
    private String insertQuery;
    private Random random;

    public void setJdbcOperations(final JdbcOperations jdbcOperations)
    {
        this.jdbcOperations = jdbcOperations;
    }

    public void setIdColumnName(final String idColumnName)
    {
        this.idColumnName = idColumnName;
    }

    public void setRandom(final Random random)
    {
        this.random = random;
    }

    public void setRandomColumnName(final String randomColumnName)
    {
        this.randomColumnName = randomColumnName;
    }

    public void setStateColumnName(final String stateColumnName)
    {
        this.stateColumnName = stateColumnName;
    }

    public void setTableName(final String tableName)
    {
        this.tableName = tableName;
    }

    public void afterPropertiesSet() throws Exception
    {
        super.afterPropertiesSet();
        if(random == null)
        {
            random = new SecureRandom();
        }
        selectQuery = "SELECT " + stateColumnName + " FROM " + tableName +
            " WHERE " + idColumnName + "=? AND " + randomColumnName + "=?";
        insertQuery = "INSERT INTO " + tableName + " (" + stateColumnName +
            ", " + randomColumnName + ") VALUES(?,?)";
    }

    private static final ResultSetExtractor EXTRACTOR = new ResultSetExtractor()
    {
        public Object extractData(final ResultSet rs) throws SQLException
        {
            if(rs.next())
            {
                return rs.getBytes(1);
            }
            return null;
        }
    };

    protected byte[] getSerializedState(final HttpServletRequest request, final String id) throws Exception
    {
        return (byte[])jdbcOperations.query(
                new PreparedStatementCreator()
                {
                    public PreparedStatement createPreparedStatement(final Connection con)
                    throws SQLException
                    {
                        final PreparedStatement statement = con.prepareStatement(
                                selectQuery);
                        final int i = id.indexOf(SEPARATOR);
                        statement.setString(1, id.substring(i + 1));
                        statement.setString(2, id.substring(0, i));
                        return statement;
                    };
                }, EXTRACTOR);
    }

    protected String storeSerializedState(final HttpServletRequest request, final byte[] state) throws Exception
    {
        final int rnd = random.nextInt();
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcOperations.update(
                new PreparedStatementCreator()
                {
                    public PreparedStatement createPreparedStatement(final Connection con)
                    throws SQLException
                    {
                        final PreparedStatement statement = con.prepareStatement(
                                insertQuery, Statement.RETURN_GENERATED_KEYS);
                        statement.setBytes(1, state);
                        statement.setInt(2, rnd);
                        return statement;
                    };
                }, keyHolder);
        return rnd + (SEPARATOR + keyHolder.getKey().toString());
    }
}
