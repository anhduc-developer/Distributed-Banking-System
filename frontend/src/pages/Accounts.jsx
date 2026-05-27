import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, InputNumber, Select, Tag, Space, message, Descriptions, Badge } from 'antd';
import { CreditCardOutlined, EyeOutlined, PlusOutlined, ReloadOutlined, HistoryOutlined } from '@ant-design/icons';
import { accountApi, customerApi } from '../api/bankApi';

const fmt = (v) => v ? Number(v).toLocaleString('vi-VN') : '0';

export default function Accounts() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [branch, setBranch] = useState('HN');
  const [createOpen, setCreateOpen] = useState(false);
  const [balanceModal, setBalanceModal] = useState(null);
  const [historyModal, setHistoryModal] = useState(null);
  const [history, setHistory] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [form] = Form.useForm();

  const loadAccounts = async () => {
    setLoading(true);
    try {
      const res = await accountApi.getByBranch(branch);
      setAccounts(res.data.data || []);
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
    setLoading(false);
  };

  useEffect(() => { loadAccounts(); }, [branch]);

  const showBalance = async (id) => {
    try {
      const res = await accountApi.getBalance(id, branch);
      setBalanceModal(res.data.data);
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
  };

  const showHistory = async (id) => {
    try {
      const res = await accountApi.getTransactions(id, branch);
      setHistory(res.data.data || []);
      setHistoryModal(id);
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleCreate = async (values) => {
    try {
      await accountApi.create(values);
      message.success('Tạo tài khoản thành công!');
      setCreateOpen(false);
      form.resetFields();
      loadAccounts();
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
  };

  const toggleStatus = async (id, currentStatus) => {
    const newStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await accountApi.updateStatus(id, branch, newStatus);
      message.success('Đã đổi trạng thái!');
      loadAccounts();
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
  };

  const loadCustomers = async () => {
    try {
      const res = await customerApi.getByBranch(branch);
      setCustomers(res.data.data || []);
    } catch (e) { }
  };

  const columns = [
    { title: 'ID', dataIndex: 'accountId', key: 'id', width: 60 },
    { title: 'KH ID', dataIndex: 'customerId', key: 'cid', width: 70 },
    {
      title: 'Chi nhánh', dataIndex: 'branchId', key: 'branch',
      render: (v) => <Tag color="blue">{v}</Tag>
    },
    {
      title: 'Số dư', dataIndex: 'balance', key: 'balance',
      render: (v) => <span style={{ fontWeight: 600, color: '#52c41a' }}>{fmt(v)} ₫</span>
    },
    {
      title: 'Trạng thái', dataIndex: 'status', key: 'status',
      render: (v) => <Badge status={v === 'ACTIVE' ? 'success' : 'error'} text={v} />
    },
    {
      title: 'Thao tác', key: 'actions', width: 240,
      render: (_, r) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => showBalance(r.accountId)}>Số dư</Button>
          <Button size="small" icon={<HistoryOutlined />} onClick={() => showHistory(r.accountId)}>Lịch sử</Button>
          <Button size="small" onClick={() => toggleStatus(r.accountId, r.status)}>
            {r.status === 'ACTIVE' ? 'Khóa' : 'Mở'}
          </Button>
        </Space>
      ),
    },
  ];

  const txnColumns = [
    { title: 'ID', dataIndex: 'transactionId', key: 'id', width: 50 },
    {
      title: 'Loại', dataIndex: 'transactionType', key: 'type',
      render: (v) => {
        const colors = {
          DEPOSIT: 'green', WITHDRAW: 'red', TRANSFER_IN: 'cyan',
          TRANSFER_OUT: 'orange', INTER_BRANCH_IN: 'purple', INTER_BRANCH_OUT: 'magenta'
        };
        return <Tag color={colors[v] || 'default'}>{v}</Tag>;
      }
    },
    {
      title: 'Số tiền', dataIndex: 'amount', key: 'amount',
      render: (v) => `${fmt(v)} ₫`
    },
    {
      title: 'Số dư sau', dataIndex: 'balanceAfter', key: 'after',
      render: (v) => v ? `${fmt(v)} ₫` : '-'
    },
    {
      title: 'Trạng thái', dataIndex: 'status', key: 'status',
      render: (v) => <Tag color={v === 'SUCCESS' ? 'green' : 'red'}>{v}</Tag>
    },
    {
      title: 'Thời gian', dataIndex: 'createdAt', key: 'time',
      render: (v) => v ? new Date(v).toLocaleString('vi-VN') : '-'
    },
  ];

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Quản lý Tài khoản</h2>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select value={branch} onChange={setBranch} style={{ width: 160 }}
            options={[
              { value: 'HN', label: 'Hà Nội' },
              { value: 'DN', label: 'Đà Nẵng' },
              { value: 'HCM', label: 'TP.HCM' },
            ]} />
          <Button icon={<ReloadOutlined />} onClick={loadAccounts}>Tải lại</Button>
          <Button type="primary" icon={<PlusOutlined />}
            onClick={() => { loadCustomers(); form.setFieldsValue({ branchId: branch }); setCreateOpen(true); }}>
            Mở tài khoản
          </Button>
        </Space>
        <Table loading={loading} dataSource={accounts} columns={columns}
          rowKey="accountId" pagination={{ pageSize: 10 }} />
      </Card>

      {/* Create Modal */}
      <Modal title="Mở tài khoản mới" open={createOpen}
        onCancel={() => setCreateOpen(false)} onOk={() => form.submit()} okText="Tạo">
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true }]}>
            <Select placeholder="Chọn khách hàng"
              options={customers.map(c => ({ value: c.customerId, label: `#${c.customerId} - ${c.fullName}` }))} />
          </Form.Item>
          <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true }]}>
            <Select options={[
              { value: 'HN', label: 'Hà Nội' },
              { value: 'DN', label: 'Đà Nẵng' },
              { value: 'HCM', label: 'TP.HCM' },
            ]} />
          </Form.Item>
          <Form.Item name="initialBalance" label="Số dư ban đầu">
            <InputNumber style={{ width: '100%' }} min={0} placeholder="0"
              formatter={v => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Balance Modal */}
      <Modal title="Tra cứu số dư" open={!!balanceModal}
        onCancel={() => setBalanceModal(null)} footer={null}>
        {balanceModal && (
          <Descriptions bordered column={1}>
            <Descriptions.Item label="Tài khoản">#{balanceModal.accountId}</Descriptions.Item>
            <Descriptions.Item label="Chủ TK">{balanceModal.customerName}</Descriptions.Item>
            <Descriptions.Item label="Chi nhánh"><Tag color="blue">{balanceModal.branchId}</Tag></Descriptions.Item>
            <Descriptions.Item label="Số dư">
              <span style={{ fontSize: 20, fontWeight: 700, color: '#52c41a' }}>
                {fmt(balanceModal.balance)} ₫
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Badge status={balanceModal.status === 'ACTIVE' ? 'success' : 'error'} text={balanceModal.status} />
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* History Modal */}
      <Modal title={`Lịch sử giao dịch — TK #${historyModal}`} open={!!historyModal}
        onCancel={() => setHistoryModal(null)} footer={null} width={800}>
        <Table dataSource={history} columns={txnColumns} rowKey="transactionId"
          pagination={{ pageSize: 10 }} size="small" />
      </Modal>
    </div>
  );
}
