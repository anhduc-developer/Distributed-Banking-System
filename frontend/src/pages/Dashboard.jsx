import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Spin, Typography } from 'antd';
import {
  BankOutlined, UserOutlined, CreditCardOutlined,
  DollarOutlined, RiseOutlined, SwapOutlined,
} from '@ant-design/icons';
import { statsApi, branchApi } from '../api/bankApi';

const { Title, Text } = Typography;

export default function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [totalBalance, setTotalBalance] = useState(null);
  const [branches, setBranches] = useState([]);
  const [txnStats, setTxnStats] = useState([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [balRes, branchRes, statsRes] = await Promise.all([
        statsApi.getTotalBalance(),
        branchApi.getAll(),
        statsApi.getTransactionSummary(),
      ]);
      setTotalBalance(balRes.data.data);
      setBranches(branchRes.data.data || []);
      setTxnStats(statsRes.data.data || []);
    } catch (err) {
      console.error('Dashboard load error:', err);
    }
    setLoading(false);
  };

  const fmt = (v) => {
    if (!v) return '0';
    return Number(v).toLocaleString('vi-VN');
  };

  if (loading) {
    return <div style={{ textAlign: 'center', paddingTop: 100 }}><Spin size="large" /></div>;
  }

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Tổng quan hệ thống</h2>
      </div>

      {/* Summary Cards */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card">
            <div className="stat-icon" style={{ background: '#e6f7ff', color: '#1677ff' }}>
              <BankOutlined />
            </div>
            <div className="stat-value">{branches.length}</div>
            <div className="stat-label">Chi nhánh</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card">
            <div className="stat-icon" style={{ background: '#f6ffed', color: '#52c41a' }}>
              <DollarOutlined />
            </div>
            <div className="stat-value">{fmt(totalBalance?.systemTotalBalance)} ₫</div>
            <div className="stat-label">Tổng số dư hệ thống</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card">
            <div className="stat-icon" style={{ background: '#fff7e6', color: '#fa8c16' }}>
              <CreditCardOutlined />
            </div>
            <div className="stat-value">
              {(totalBalance?.hanoiAccountCount || 0) +
                (totalBalance?.danangAccountCount || 0) +
                (totalBalance?.hcmAccountCount || 0)}
            </div>
            <div className="stat-label">Tổng tài khoản</div>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card">
            <div className="stat-icon" style={{ background: '#f9f0ff', color: '#722ed1' }}>
              <SwapOutlined />
            </div>
            <div className="stat-value">
              {txnStats.reduce((sum, s) => sum + (s.totalTransactions || 0), 0)}
            </div>
            <div className="stat-label">Tổng giao dịch</div>
          </Card>
        </Col>
      </Row>

      {/* Branch Balance Cards */}
      <Title level={4} style={{ marginBottom: 16 }}>Số dư theo chi nhánh</Title>
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {[
          { name: 'Hà Nội', id: 'HN', balance: totalBalance?.hanoiTotalBalance, count: totalBalance?.hanoiAccountCount, color: '#1677ff' },
          { name: 'Đà Nẵng', id: 'DN', balance: totalBalance?.danangTotalBalance, count: totalBalance?.danangAccountCount, color: '#52c41a' },
          { name: 'TP.HCM', id: 'HCM', balance: totalBalance?.hcmTotalBalance, count: totalBalance?.hcmAccountCount, color: '#fa8c16' },
        ].map(b => (
          <Col xs={24} sm={8} key={b.id}>
            <Card style={{ borderTop: `3px solid ${b.color}` }}>
              <Statistic
                title={<><BankOutlined /> {b.name} ({b.id})</>}
                value={fmt(b.balance)}
                suffix="₫"
                valueStyle={{ color: b.color, fontWeight: 700 }}
              />
              <Text type="secondary">{b.count || 0} tài khoản</Text>
            </Card>
          </Col>
        ))}
      </Row>

      {/* Transaction Stats Table */}
      <Title level={4} style={{ marginBottom: 16 }}>Thống kê giao dịch</Title>
      <Card>
        <Table
          dataSource={txnStats}
          rowKey="branchId"
          pagination={false}
          columns={[
            {
              title: 'Chi nhánh', dataIndex: 'branchName', key: 'branchName',
              render: (t) => <Tag color="blue">{t}</Tag>
            },
            { title: 'Tổng GD', dataIndex: 'totalTransactions', key: 'total' },
            {
              title: 'Gửi tiền', dataIndex: 'depositCount', key: 'deposit',
              render: (v) => <Tag color="green">{v}</Tag>
            },
            {
              title: 'Rút tiền', dataIndex: 'withdrawCount', key: 'withdraw',
              render: (v) => <Tag color="red">{v}</Tag>
            },
            {
              title: 'Chuyển khoản', dataIndex: 'transferCount', key: 'transfer',
              render: (v) => <Tag color="purple">{v}</Tag>
            },
            {
              title: 'Tổng gửi', dataIndex: 'totalDepositAmount', key: 'depAmt',
              render: (v) => `${fmt(v)} ₫`
            },
            {
              title: 'Tổng rút', dataIndex: 'totalWithdrawAmount', key: 'witAmt',
              render: (v) => `${fmt(v)} ₫`
            },
          ]}
        />
      </Card>
    </div>
  );
}
