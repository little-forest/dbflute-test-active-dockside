package org.docksidestage.dockside.dbflute.whitebox.bhv;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbflute.cbean.result.ListResultBean;
import org.dbflute.cbean.scoping.OrQuery;
import org.dbflute.cbean.scoping.ScalarQuery;
import org.dbflute.cbean.scoping.SubQuery;
import org.dbflute.cbean.scoping.UnionQuery;
import org.dbflute.exception.SpecifyColumnTwoOrMoreColumnException;
import org.dbflute.exception.SpecifyColumnWithDerivedReferrerException;
import org.dbflute.exception.SpecifyDerivedReferrerTwoOrMoreException;
import org.dbflute.hook.CallbackContext;
import org.dbflute.hook.SqlLogHandler;
import org.dbflute.hook.SqlLogInfo;
import org.dbflute.util.DfTypeUtil;
import org.dbflute.util.Srl;
import org.docksidestage.dockside.dbflute.bsentity.dbmeta.MemberServiceDbm;
import org.docksidestage.dockside.dbflute.bsentity.dbmeta.SummaryWithdrawalDbm;
import org.docksidestage.dockside.dbflute.cbean.MemberCB;
import org.docksidestage.dockside.dbflute.cbean.MemberServiceCB;
import org.docksidestage.dockside.dbflute.cbean.PurchaseCB;
import org.docksidestage.dockside.dbflute.exbhv.MemberBhv;
import org.docksidestage.dockside.dbflute.exbhv.MemberServiceBhv;
import org.docksidestage.dockside.dbflute.exbhv.SummaryWithdrawalBhv;
import org.docksidestage.dockside.dbflute.exentity.Member;
import org.docksidestage.dockside.dbflute.exentity.MemberLogin;
import org.docksidestage.dockside.unit.UnitContainerTestCase;

/**
 * @author jflute
 * @since 0.6.0 (2008/01/16 Wednesday)
 */
public class WxBhvScalarSelectBasicTest extends UnitContainerTestCase {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    private MemberBhv memberBhv;
    private MemberServiceBhv memberServiceBhv;
    private SummaryWithdrawalBhv summaryWithdrawalBhv;

    // ===================================================================================
    //                                                                               Basic
    //                                                                               =====
    public void test_ScalarSelect_max() {
        // ## Arrange ##
        LocalDate expected = memberBhv.selectEntityWithDeletedCheck(cb -> {
            cb.specify().columnBirthdate();
            cb.query().setMemberStatusCode_Equal_Formalized();
            cb.query().setBirthdate_IsNotNull();
            cb.query().addOrderBy_Birthdate_Desc();
            cb.fetchFirst(1);
        }).getBirthdate();

        // ## Act ##
        memberBhv.selectScalar(LocalDate.class).max(cb -> {
            cb.specify().columnBirthdate();
            cb.query().setMemberStatusCode_Equal_Formalized();
        }).alwaysPresent(birthdate -> {
            /* ## Assert ## */
            assertEquals(expected, birthdate);
        });
    }

    // ===================================================================================
    //                                                                      (Unique) Count
    //                                                                      ==============
    public void test_ScalarSelect_count() {
        // ## Arrange ##
        int countAll = memberBhv.selectCount(countCB -> {});

        // ## Act ##
        Integer scalarCount = memberBhv.selectScalar(Integer.class).count(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().columnMemberId();
            }
        });

        // ## Assert ##
        assertEquals(countAll, scalarCount);
    }

    // ===================================================================================
    //                                                                      Count Distinct
    //                                                                      ==============
    public void test_ScalarSelect_countDistinct_basic() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {});

        HashSet<String> statusSet = new HashSet<String>();
        for (Member member : memberList) {
            statusSet.add(member.getMemberStatusCode());
        }

        // ## Act ##
        Integer kindCount = memberBhv.selectScalar(Integer.class).countDistinct(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().columnMemberStatusCode();
            }
        });

        // ## Assert ##
        assertEquals(statusSet.size(), kindCount);
    }

    public void test_ScalarSelect_countDistinct_noHist() {
        // ## Arrange ##

        // ## Act ##
        Integer kindCount = memberBhv.selectScalar(Integer.class).countDistinct(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().columnMemberStatusCode();
                cb.query().setMemberName_Equal("no exist");
            }
        });

        // ## Assert ##
        assertEquals(0, kindCount);
    }

    // ===================================================================================
    //                                                                     DerivedReferrer
    //                                                                     ===============
    public void test_ScalarSelect_DerivedReferrer_basic() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.specify().derivedPurchase().max(new SubQuery<PurchaseCB>() {
                public void query(PurchaseCB subCB) {
                    subCB.specify().columnPurchasePrice();
                    subCB.query().setPaymentCompleteFlg_Equal_True();
                    subCB.query().setPurchasePrice_GreaterEqual(800);
                }
            }, Member.ALIAS_productKindCount, op -> op.coalesce(0));
            cb.query().setMemberStatusCode_Equal_Formalized();
        });
        Integer expected = memberList.stream().mapToInt(member -> member.getProductKindCount()).sum();

        // ## Act ##
        memberBhv.selectScalar(Integer.class).sum(cb -> {
            cb.specify().derivedPurchase().max(purchaseCB -> {
                purchaseCB.specify().columnPurchasePrice();
                purchaseCB.query().setPaymentCompleteFlg_Equal_True();
                purchaseCB.query().setPurchasePrice_GreaterEqual(800);
            }, null);
            cb.query().setMemberStatusCode_Equal_Formalized();
        }).alwaysPresent(sum -> {
            /* ## Assert ## */
            log("sum = " + sum);
            assertEquals(expected, sum);
        });
    }

    public void test_ScalarSelect_DerivedReferrer_with_UnionQuery() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.specify().derivedPurchase().max(new SubQuery<PurchaseCB>() {
                public void query(PurchaseCB subCB) {
                    subCB.specify().columnPurchasePrice();
                    subCB.query().setPaymentCompleteFlg_Equal_True();
                    subCB.query().setPurchasePrice_GreaterEqual(800);
                }
            }, Member.ALIAS_productKindCount, op -> op.coalesce(0));
            cb.orScopeQuery(new OrQuery<MemberCB>() {
                public void query(MemberCB orCB) {
                    orCB.query().setMemberStatusCode_Equal_Withdrawal();
                    orCB.query().setMemberName_LikeSearch("S", op -> op.likePrefix());
                }
            });
        });
        Integer expected = memberList.stream().mapToInt(member -> member.getProductKindCount()).sum();

        // ## Act ##
        memberBhv.selectScalar(Integer.class).sum(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().derivedPurchase().max(new SubQuery<PurchaseCB>() {
                    public void query(PurchaseCB subCB) {
                        subCB.specify().columnPurchasePrice();
                        subCB.query().setPaymentCompleteFlg_Equal_True();
                        subCB.query().setPurchasePrice_GreaterEqual(800);
                    }
                }, null);
                cb.query().setMemberStatusCode_Equal_Withdrawal();
                cb.union(new UnionQuery<MemberCB>() {
                    public void query(MemberCB unionCB) {
                        unionCB.query().setMemberName_LikeSearch("S", op -> op.likePrefix());
                    }
                });
            }
        }).alwaysPresent(sum -> {
            /* ## Assert ## */
            log("sum = " + sum);
            assertEquals(expected, sum);
        });
    }

    // ===================================================================================
    //                                                                          UnionQuery
    //                                                                          ==========
    public void test_ScalarSelect_with_UnionQuery_basic_sum() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberServiceAsOne();
        });

        Integer expected = memberList.stream().mapToInt(member -> {
            return member.getMemberServiceAsOne().map(service -> service.getServicePointCount()).get();
        }).sum();
        final Set<String> markSet = new HashSet<String>();
        CallbackContext.setSqlLogHandlerOnThread(info -> {
            MemberServiceDbm dbm = MemberServiceDbm.getInstance();
            String displaySql = info.getDisplaySql();
            assertTrue(Srl.contains(displaySql, dbm.columnMemberServiceId().getColumnDbName()));
            assertTrue(Srl.contains(displaySql, dbm.columnServicePointCount().getColumnDbName()));
            assertFalse(Srl.contains(displaySql, dbm.columnServiceRankCode().getColumnDbName()));
            markSet.add("handle");
        });

        // ## Act ##
        try {
            memberServiceBhv.selectScalar(Integer.class).sum(cb -> {
                cb.specify().columnServicePointCount();
                cb.union(unionCB -> {});
            }).alwaysPresent(sum -> {
                /* ## Assert ## */
                log("sum = " + sum);
                assertEquals("should be selected uniquely", expected, sum);
                assertTrue(markSet.contains("handle"));
            });
        } finally {
            CallbackContext.clearSqlLogHandlerOnThread();
        }
    }

    public void test_ScalarSelect_with_UnionQuery_PrimaryKey_sum() {
        // ## Arrange ##
        final Set<String> markSet = new HashSet<String>();
        CallbackContext.setSqlLogHandlerOnThread(info -> {
            MemberServiceDbm dbm = MemberServiceDbm.getInstance();
            String displaySql = info.getDisplaySql();
            assertTrue(Srl.contains(displaySql, dbm.columnMemberServiceId().getColumnDbName()));
            assertFalse(Srl.contains(displaySql, dbm.columnServicePointCount().getColumnDbName()));
            assertFalse(Srl.contains(displaySql, dbm.columnServiceRankCode().getColumnDbName()));
            markSet.add("handle");
        });

        // ## Act ##
        try {
            memberServiceBhv.selectScalar(Integer.class).sum(new ScalarQuery<MemberServiceCB>() {
                public void query(MemberServiceCB cb) {
                    cb.specify().columnMemberServiceId();
                    cb.union(new UnionQuery<MemberServiceCB>() {
                        public void query(MemberServiceCB unionCB) {
                        }
                    });
                }
            }).alwaysPresent(sum -> {
                /* ## Assert ## */
                log("sum = " + sum);
                assertTrue(markSet.contains("handle"));
            });
        } finally {
            CallbackContext.clearSqlLogHandlerOnThread();
        }
    }

    public void test_ScalarSelect_with_UnionQuery_noPrimaryKey_sum() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.specify().derivedPurchase().max(purchaseCB -> {
                purchaseCB.specify().columnPurchasePrice();
            }, Member.ALIAS_highestPurchasePrice);
            cb.query().setMemberStatusCode_Equal_Withdrawal();
            cb.query().addSpecifiedDerivedOrderBy_Desc(Member.ALIAS_highestPurchasePrice);
        });

        Integer expected = memberList.stream().mapToInt(member -> member.getHighestPurchasePrice()).sum();
        final Set<String> markSet = new HashSet<String>();
        CallbackContext.setSqlLogHandlerOnThread(new SqlLogHandler() {
            public void handle(SqlLogInfo info) {
                String displaySql = info.getDisplaySql();
                SummaryWithdrawalDbm dbm = SummaryWithdrawalDbm.getInstance();
                assertTrue(Srl.contains(displaySql, dbm.columnMaxPurchasePrice().getColumnDbName()));
                assertTrue(Srl.contains(displaySql, dbm.columnWithdrawalDatetime().getColumnDbName()));
                markSet.add("handle");
            }
        });

        // ## Act ##
        try {
            summaryWithdrawalBhv.selectScalar(Integer.class).sum(cb -> {
                cb.specify().columnMaxPurchasePrice();
                cb.union(unionCB -> {});
            }).alwaysPresent(sum -> {
                /* ## Assert ## */
                log("sum = " + sum);
                assertEquals("should be selected uniquely", expected, sum);
                assertTrue(markSet.contains("handle"));
            });
        } finally {
            CallbackContext.clearSqlLogHandlerOnThread();
        }
    }

    // ===================================================================================
    //                                                                     Relation Column
    //                                                                     ===============
    public void test_ScalarSelect_relation_basic() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
        });
        int expected = 0;
        for (Member member : memberList) {
            expected = expected + member.getMemberStatus().get().getDisplayOrder();
        }

        // ## Act ##
        Integer sum = memberBhv.selectScalar(Integer.class).sum(cb -> {
            cb.specify().specifyMemberStatus().columnDisplayOrder();
        }).get();

        // ## Assert ##
        assertEquals(expected, sum);
    }

    public void test_ScalarSelect_relation_union() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
        });
        int expected = 0;
        for (Member member : memberList) {
            expected = expected + member.getMemberStatus().get().getDisplayOrder();
        }

        // ## Act ##
        Integer sum = memberBhv.selectScalar(Integer.class).sum(cb -> {
            cb.specify().specifyMemberStatus().columnDisplayOrder();
            cb.union(unionCB -> {});
        }).get();

        // ## Assert ##
        assertEquals(expected, sum);
    }

    public void test_ScalarSelect_relation_DerivedReferrer_basic() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
        });
        memberBhv.load(memberList, loader -> {
            loader.pulloutMemberStatus().loadMemberLogin(refCB -> {
                refCB.query().addOrderBy_MemberLoginId_Desc();
            });
        });
        Long expected = 0L;
        for (Member member : memberList) {
            List<MemberLogin> loginList = member.getMemberStatus().get().getMemberLoginList();
            long currentId = !loginList.isEmpty() ? loginList.get(0).getMemberLoginId() : 0;
            expected = expected + currentId;
        }
        final Set<String> sqlSet = new HashSet<String>();
        CallbackContext.setSqlLogHandlerOnThread(new SqlLogHandler() {
            public void handle(SqlLogInfo info) {
                sqlSet.add(info.getDisplaySql());
            }
        });

        // ## Act ##
        try {
            Long sum = memberBhv.selectScalar(Long.class).sum(cb -> {
                cb.specify().specifyMemberStatus().derivedMemberLogin().max(loginCB -> {
                    loginCB.specify().columnMemberLoginId();
                }, null);
            }).get();

            // ## Assert ##
            assertEquals(expected, sum);
            String sql = sqlSet.iterator().next();
            assertContains(sql, "select sum((select max(sub1loc.MEMBER_LOGIN_ID)");
        } finally {
            CallbackContext.clearSqlLogHandlerOnThread();
        }
    }

    public void test_ScalarSelect_relation_DerivedReferrer_union() {
        // ## Arrange ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
        });
        memberBhv.load(memberList, loader -> {
            loader.pulloutMemberStatus().loadMemberLogin(loginCB -> {
                loginCB.query().addOrderBy_MemberLoginId_Desc();
            });
        });
        Long expected = 0L;
        for (Member member : memberList) {
            List<MemberLogin> loginList = member.getMemberStatus().get().getMemberLoginList();
            long currentId = !loginList.isEmpty() ? loginList.get(0).getMemberLoginId() : 0;
            expected = expected + currentId;
        }
        final Set<String> sqlSet = new HashSet<String>();
        CallbackContext.setSqlLogHandlerOnThread(new SqlLogHandler() {
            public void handle(SqlLogInfo info) {
                sqlSet.add(info.getDisplaySql());
            }
        });

        // ## Act ##
        try {
            Long sum = memberBhv.selectScalar(Long.class).sum(cb -> {
                cb.specify().specifyMemberStatus().derivedMemberLogin().max(loginCB -> {
                    loginCB.specify().columnMemberLoginId();
                    loginCB.query().setMobileLoginFlg_Equal_True();
                    loginCB.unionAll(unionCB -> unionCB.query().setMobileLoginFlg_Equal_False());
                }, null);
            }).get();

            // ## Assert ##
            assertEquals(expected, sum);
            String sql = sqlSet.iterator().next();
            assertContains(sql, "select sum((select max(sub1main.MEMBER_LOGIN_ID)");
            assertContains(sql, "union all ");
            assertContains(sql, "from (select sub1loc.LOGIN_MEMBER_STATUS_CODE, sub1loc.MEMBER_LOGIN_ID");
        } finally {
            CallbackContext.clearSqlLogHandlerOnThread();
        }
    }

    // ===================================================================================
    //                                                                             Illegal
    //                                                                             =======
    public void test_ScalarSelect_duplicated_basic() {
        // ## Arrange ##
        // ## Act ##
        try {
            memberBhv.selectScalar(Date.class).max(cb -> {
                cb.specify().columnMemberAccount();
                cb.specify().columnBirthdate();
            });

            // ## Assert ##
            fail();
        } catch (SpecifyColumnTwoOrMoreColumnException e) {
            // OK
            log(e.getMessage());
        }
    }

    public void test_ScalarSelect_duplicated_both() {
        // ## Arrange ##
        // ## Act ##
        try {
            memberBhv.selectScalar(Date.class).max(cb -> {
                cb.specify().columnMemberAccount();
                cb.specify().derivedPurchase().max(purchaseCB -> {
                    purchaseCB.specify().columnPurchaseCount();
                }, null);
            });

            // ## Assert ##
            fail();
        } catch (SpecifyColumnWithDerivedReferrerException e) {
            // OK
            log(e.getMessage());
        }
    }

    public void test_ScalarSelect_duplicated_DerivedReferrer() {
        // ## Arrange ##
        // ## Act ##
        try {
            memberBhv.selectScalar(Date.class).max(new ScalarQuery<MemberCB>() {
                public void query(MemberCB cb) {
                    cb.specify().derivedPurchase().max(new SubQuery<PurchaseCB>() {
                        public void query(PurchaseCB subCB) {
                            subCB.specify().columnPurchaseCount();
                        }
                    }, null);
                    cb.specify().derivedPurchase().max(new SubQuery<PurchaseCB>() {
                        public void query(PurchaseCB subCB) {
                            subCB.specify().columnPurchasePrice();
                        }
                    }, null);
                }
            });

            // ## Assert ##
            fail();
        } catch (SpecifyDerivedReferrerTwoOrMoreException e) {
            // OK
            log(e.getMessage());
        }
    }

    // ===================================================================================
    //                                                                              Option
    //                                                                              ======
    public void test_ScalarSelect_option_basic() {
        // ## Arrange ##
        int coalesce = 7849238;

        // ## Act ##
        memberBhv.selectScalar(Integer.class).max(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().columnMemberId();
                cb.query().setMemberStatusCode_Equal_Formalized();
                cb.query().setMemberName_Equal("no exist");
            }
        }, op -> op.coalesce(coalesce)).alwaysPresent(max -> {
            /* ## Assert ## */
            assertEquals(Integer.valueOf(coalesce), max);
        });
    }

    public void test_ScalarSelect_option_date() {
        // ## Arrange ##
        String coalesce = "2011-12-12";

        // ## Act ##
        memberBhv.selectScalar(Date.class).max(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().columnBirthdate();
                cb.query().setMemberStatusCode_Equal_Formalized();
                cb.query().setMemberName_Equal("no exist");
            }
        }, op -> op.coalesce(coalesce)).alwaysPresent(birthdate -> {
            /* ## Assert ## */
            assertEquals(coalesce, DfTypeUtil.toString(birthdate, "yyyy-MM-dd"));
        });
    }

    public void test_ScalarSelect_option_DerivedReferrer_basic() {
        // ## Arrange ##
        int coalesce = 7849238;

        // ## Act ##
        memberBhv.selectScalar(Integer.class).max(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().derivedPurchase().avg(new SubQuery<PurchaseCB>() {
                    public void query(PurchaseCB subCB) {
                        subCB.specify().columnPurchasePrice();
                    }
                }, null);
                cb.query().setMemberStatusCode_Equal_Formalized();
                cb.query().setMemberName_Equal("no exist");
            }
        }, op -> op.coalesce(coalesce)).alwaysPresent(max -> {
            /* ## Assert ## */
            assertEquals(Integer.valueOf(coalesce), max);
        });
    }

    public void test_ScalarSelect_option_DerivedReferrer_severalFunction() {
        // ## Arrange ##
        int coalesce = 7849238;

        // ## Act ##
        memberBhv.selectScalar(Integer.class).max(new ScalarQuery<MemberCB>() {
            public void query(MemberCB cb) {
                cb.specify().derivedPurchase().avg(new SubQuery<PurchaseCB>() {
                    public void query(PurchaseCB subCB) {
                        subCB.specify().columnPurchasePrice();
                    }
                }, null);
                cb.query().setMemberStatusCode_Equal_Formalized();
                cb.query().setMemberName_Equal("no exist");
            }
        }, op -> op.coalesce(coalesce).round(2)).alwaysPresent(max -> {
            /* ## Assert ## */
            assertEquals(Integer.valueOf(coalesce), max);
        });
    }
}
