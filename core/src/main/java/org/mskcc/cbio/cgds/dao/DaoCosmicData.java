/** Copyright (c) 2012 Memorial Sloan-Kettering Cancer Center.
**
** This library is free software; you can redistribute it and/or modify it
** under the terms of the GNU Lesser General Public License as published
** by the Free Software Foundation; either version 2.1 of the License, or
** any later version.
**
** This library is distributed in the hope that it will be useful, but
** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
** documentation provided hereunder is on an "as is" basis, and
** Memorial Sloan-Kettering Cancer Center 
** has no obligations to provide maintenance, support,
** updates, enhancements or modifications.  In no event shall
** Memorial Sloan-Kettering Cancer Center
** be liable to any party for direct, indirect, special,
** incidental or consequential damages, including lost profits, arising
** out of the use of this software and its documentation, even if
** Memorial Sloan-Kettering Cancer Center 
** has been advised of the possibility of such damage.  See
** the GNU Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public License
** along with this library; if not, write to the Free Software Foundation,
** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
**/

package org.mskcc.cbio.cgds.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.mskcc.cbio.cgds.model.CosmicMutationFrequency;
import org.mskcc.cbio.cgds.model.ExtendedMutation;

/**
 *
 * @author jgao
 */
public class DaoCosmicData {
    public static int addCosmic(CosmicMutationFrequency cosmic) throws DaoException {
            if (!MySQLbulkLoader.isBulkLoad()) {
                throw new DaoException("You have to turn on MySQLbulkLoader in order to insert mutations");
            } else {

                    // use this code if bulk loading
                    // write to the temp file maintained by the MySQLbulkLoader
                    MySQLbulkLoader.getMySQLbulkLoader("cosmic_mutation").insertRecord(
                            cosmic.getId(),
                            cosmic.getChr(),
                            Long.toString(cosmic.getStartPosition()),
                            cosmic.getReferenceAllele(),
                            cosmic.getTumorSeqAllele(),
                            cosmic.getStrand(),
                            cosmic.getCds(),
                            Long.toString(cosmic.getEntrezGeneId()),
                            cosmic.getAminoAcidChange(),
                            Integer.toString(cosmic.getFrequency()),
                            cosmic.getKeyword());

                    return 1;
            }
    }
    
    /**
     * 
     * @param mutations
     * @return Map of event id to map of aa change to count
     * @throws DaoException 
     */
    public static Map<Long, Map<String,Integer>> getCosmicForMutationEvents(
            List<ExtendedMutation> mutations) throws DaoException {
        Set<String> mutKeywords = new HashSet<String>();
        for (ExtendedMutation mut : mutations) {
            mutKeywords.add(mut.getKeyword());
        }
        
        Map<String, List<CosmicMutationFrequency>> map = 
                DaoCosmicData.getCosmicDataByKeyword(mutKeywords);
        Map<Long, Map<String,Integer>> ret
                = new HashMap<Long, Map<String,Integer>>(map.size());
        for (ExtendedMutation mut : mutations) {
            String keyword = mut.getKeyword();
            List<CosmicMutationFrequency> cmfs = map.get(keyword);
            if (cmfs==null) {
                continue;
            }
            Map<String,Integer> mapSI = new HashMap<String,Integer>();
            for (CosmicMutationFrequency cmf : cmfs) {
                mapSI.put(cmf.getAminoAcidChange(), cmf.getFrequency());
            }
            ret.put(mut.getMutationEventId(), mapSI);
        }
        return ret;
    }
    
    /**
     * 
     * @param keyword
     * @return Map<keyword, List<cosmic>>
     * @throws DaoException 
     */
    public static Map<String,List<CosmicMutationFrequency>> getCosmicDataByKeyword(Collection<String> keywordS) throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoCosmicData.class);
            pstmt = con.prepareStatement("SELECT * FROM cosmic_mutation "
                    + " WHERE KEYWORD in ('" + StringUtils.join(keywordS, "','") + "')");
            rs = pstmt.executeQuery();
            Map<String,List<CosmicMutationFrequency>> ret = new HashMap<String,List<CosmicMutationFrequency>>();
            while (rs.next()) {
                CosmicMutationFrequency cmf = extractCosmic(rs);
                List<CosmicMutationFrequency> list = ret.get(cmf.getKeyword());
                if (list==null) {
                    list = new ArrayList<CosmicMutationFrequency>();
                    ret.put(cmf.getKeyword(), list);
                }
                list.add(cmf);
            }
            return ret;
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoCosmicData.class, con, pstmt, rs);
        }
    }
    
    private static CosmicMutationFrequency extractCosmic(ResultSet rs) throws SQLException {
        String id = rs.getString("COSMIC_MUTATION_ID");
        long entrez = rs.getLong("ENTREZ_GENE_ID");
        String aa = rs.getString("PROTEIN_CHANGE");
        String keyword = rs.getString("KEYWORD");
        int count = rs.getInt("COUNT");
        return new CosmicMutationFrequency(id, entrez, aa, keyword, count);
    }
    
    public static void deleteAllRecords() throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = JdbcUtil.getDbConnection(DaoCosmicData.class);
            pstmt = con.prepareStatement("TRUNCATE TABLE cosmic_mutation");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoCosmicData.class, con, pstmt, rs);
        }
    }
}