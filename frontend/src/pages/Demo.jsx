import React, { useState, useEffect } from 'react';
import { Card, Button, Select, InputNumber, Switch, Table, Tag, Row, Col, Space, message, Alert, Divider, Typography } from 'antd';
import {
  ThunderboltOutlined, ExperimentOutlined, LockOutlined, UnlockOutlined,
  PoweroffOutlined, ReloadOutlined, HistoryOutlined
} from '@ant-design/icons';
import { demoApi } from '../api/bankApi';

const { Title, Text } = Typography;
const fmt = (v) => v ? Number(v).toLocaleString('vi-VN') : '0';

export default function Demo() {
  const [siteDownBranch, setSiteDownBranch] = useState('HCM');
  const [siteDownEnabled, setSiteDownEnabled] = useState(false);
  const [siteDownLoading, setSiteDownLoading] = useState(false);

  const [concBranch, setConcBranch] = useState('HN');
  const [concAccountId, setConcAccountId] = useState(1);
  const [concAmount, setConcAmount] = useState(3000000);
  const [lockResult, setLockResult] = useState(null);
  const [noLockResult, setNoLockResult] = useState(null);
  const [lockLoading, setLockLoading] = useState(false);
  const [noLockLoading, setNoLockLoading] = useState(false);

  const [txnLogs, setTxnLogs] = useState([]);
  const [logsLoading, setLogsLoading] = useState(false);

  // Site Down simulation
  const handleSiteDown = async () => {
    setSiteDownLoading(true);
    try {
      const res = await demoApi.simulateSiteDown(siteDownBranch, siteDownEnabled);
      message.success(res.data.data?.status || 'Done');
    } catch (err) {
      message.error(err.response?.data?.message || err.message);
    }
    setSiteDownLoading(false);
  };

  const handleClearSiteDown = async () => {
    try {
      await demoApi.simulateSiteDown('HN', false);
      setSiteDownEnabled(false);
      message.success('All sites are now UP');
    } catch (err) {
      message.error(err.message);
    }
  };

  // Concurrent Withdraw WITH lock
  const handleWithLock = async () => {
    setLockLoading(true);
    setLockResult(null);
    try {
      const res = await demoApi.concurrentWithdraw(concBranch, concAccountId, concAmount);
      setLockResult(res.data.data);
    } catch (err) {
      message.error(err.response?.data?.message || err.message);
    }
    setLockLoading(false);
  };

  // Concurrent Withdraw WITHOUT lock
  const handleWithoutLock = async () => {
    setNoLockLoading(true);
    setNoLockResult(null);
    try {
      const res = await demoApi.concurrentWithdrawNoLock(concBranch, concAccountId, concAmount);
      setNoLockResult(res.data.data);
    } catch (err) {
      message.error(err.response?.data?.message || err.message);
    }
    setNoLockLoading(false);
  };

  // Load 2PC logs
  const loadTxnLogs = async () => {
    setLogsLoading(true);
    try {
      const res = await demoApi.getDistributedTxnLogs();
      setTxnLogs(res.data.data || []);
    } catch (err) {
      message.error(err.response?.data?.message || err.message);
    }
    setLogsLoading(false);
  };

  const renderDemoLog = (result) => {
    if (!result) return null;
    const logs = result.logs || [];
    return (
      <div className="demo-log">
        <pre>
          {logs.map((line, i) => {
            let cls = '';
            if (line.includes('✅') || line.includes('SUCCESS') || line.includes('CORRECT')) cls = 'log-success';
            else if (line.includes('❌') || line.includes('ERROR') || line.includes('LOST UPDATE')) cls = 'log-error';
            else if (line.includes('⚠️') || line.includes('WARNING')) cls = 'log-warning';
            return <div key={i} className={cls}>{line}</div>;
          })}
        </pre>
        <Divider style={{ borderColor: 'rgba(255,255,255,0.1)' }} />
        <Space>
          <Tag color={result.correct ? 'green' : 'red'}>
            {result.correct ? '✅ CORRECT' : '❌ INCORRECT'}
          </Tag>
          <Text style={{ color: '#aaa' }}>
            Initial: {fmt(result.initialBalance)} | Expected: {fmt(result.expectedBalance)} | Actual: {fmt(result.actualBalance)}
          </Text>
        </Space>
      </div>
    );
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>🧪 Demo & Test</h2>
        <p>Mô phỏng lỗi, demo concurrency control, xem log 2PC</p>
      </div>

      {/* 1. SITE DOWN SIMULATION */}
      <Card
        title={<><PoweroffOutlined style={{ color: '#ff4d4f' }} /> Mô phỏng Site Down</>}
        style={{ borderTop: '3px solid #ff4d4f', marginBottom: 24 }}
      >
        <Alert
          message="Bật toggle để mô phỏng site (chi nhánh) bị down. Mọi request đến site đó sẽ trả lỗi 503."
          type="info" showIcon style={{ marginBottom: 16 }}
        />
        <Space>
          <Select value={siteDownBranch} onChange={setSiteDownBranch} style={{ width: 160 }}
            options={[
              { value: 'HN', label: '🏛️ Hà Nội' },
              { value: 'DN', label: '🏛️ Đà Nẵng' },
              { value: 'HCM', label: '🏛️ TP.HCM' },
            ]}
          />
          <Switch
            checked={siteDownEnabled}
            onChange={setSiteDownEnabled}
            checkedChildren="DOWN"
            unCheckedChildren="UP"
          />
          <Button type="primary" danger loading={siteDownLoading} onClick={handleSiteDown}>
            Áp dụng
          </Button>
          <Button onClick={handleClearSiteDown}>
            Tắt tất cả
          </Button>
        </Space>
      </Card>

      {/* 2. CONCURRENT WITHDRAW */}
      <Card
        title={<><ExperimentOutlined style={{ color: '#722ed1' }} /> Demo Concurrent Withdraw (Concurrency Control)</>}
        style={{ borderTop: '3px solid #722ed1', marginBottom: 24 }}
      >
        <Alert
          message="Demo 2 thread cùng rút tiền 1 tài khoản — so sánh CÓ lock vs KHÔNG lock"
          description="Có lock (SELECT FOR UPDATE): kết quả ĐÚNG | Không lock: có thể xảy ra Lost Update"
          type="warning" showIcon style={{ marginBottom: 16 }}
        />

        <Space style={{ marginBottom: 16 }} wrap>
          <Select value={concBranch} onChange={setConcBranch} style={{ width: 140 }}
            options={[
              { value: 'HN', label: '🏛️ Hà Nội' },
              { value: 'DN', label: '🏛️ Đà Nẵng' },
              { value: 'HCM', label: '🏛️ TP.HCM' },
            ]}
          />
          <span>Account ID:</span>
          <InputNumber value={concAccountId} onChange={setConcAccountId} min={1} style={{ width: 100 }} />
          <span>Mỗi thread rút:</span>
          <InputNumber value={concAmount} onChange={setConcAmount} min={1000} step={1000000} style={{ width: 150 }}
            formatter={v => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')} />
          <span>₫</span>
        </Space>

        <Row gutter={16}>
          <Col span={12}>
            <Button type="primary" loading={lockLoading} onClick={handleWithLock} block
              icon={<LockOutlined />} style={{ background: '#52c41a', borderColor: '#52c41a', marginBottom: 8 }}>
              CÓ Lock (SELECT FOR UPDATE)
            </Button>
            {renderDemoLog(lockResult)}
          </Col>
          <Col span={12}>
            <Button type="primary" loading={noLockLoading} onClick={handleWithoutLock} block
              icon={<UnlockOutlined />} danger style={{ marginBottom: 8 }}>
              KHÔNG Lock (Lost Update!)
            </Button>
            {renderDemoLog(noLockResult)}
          </Col>
        </Row>
      </Card>

      {/* 3. DISTRIBUTED TRANSACTION LOGS */}
      <Card
        title={<><HistoryOutlined style={{ color: '#fa8c16' }} /> Log Giao dịch Phân tán (2PC)</>}
        style={{ borderTop: '3px solid #fa8c16' }}
        extra={<Button icon={<ReloadOutlined />} onClick={loadTxnLogs}>Tải logs</Button>}
      >
        <Alert
          message="Đây là log bảng distributed_transaction_log — ghi lại mọi bước của 2-Phase Commit"
          type="info" showIcon style={{ marginBottom: 16 }}
        />
        <Table
          loading={logsLoading}
          dataSource={txnLogs}
          rowKey="txnId"
          pagination={{ pageSize: 10 }}
          columns={[
            { title: 'TXN ID', dataIndex: 'txnId', ellipsis: true, width: 200,
              render: (v) => <Tag color="orange" style={{ fontSize: 11 }}>{v}</Tag> },
            { title: 'Loại', dataIndex: 'txnType', render: (v) => <Tag color="purple">{v}</Tag> },
            { title: 'Trạng thái', dataIndex: 'status',
              render: (v) => {
                const colors = { STARTED: 'default', PREPARING: 'processing', COMMITTING: 'blue',
                  COMMITTED: 'green', ABORTING: 'orange', ABORTED: 'red' };
                return <Tag color={colors[v] || 'default'}>{v}</Tag>;
              }},
            { title: 'Nguồn', dataIndex: 'sourceBranch', render: (v) => <Tag color="blue">{v}</Tag> },
            { title: 'Đích', dataIndex: 'destBranch', render: (v) => <Tag color="cyan">{v}</Tag> },
            { title: 'Số tiền', dataIndex: 'amount', render: (v) => `${fmt(v)} ₫` },
            { title: 'TK nguồn', dataIndex: 'sourceAccountId' },
            { title: 'TK đích', dataIndex: 'destAccountId' },
            { title: 'Lỗi', dataIndex: 'errorMessage', ellipsis: true,
              render: (v) => v ? <Text type="danger">{v}</Text> : '-' },
            { title: 'Thời gian', dataIndex: 'createdAt',
              render: (v) => v ? new Date(v).toLocaleString('vi-VN') : '-' },
          ]}
        />
      </Card>
    </div>
  );
}
