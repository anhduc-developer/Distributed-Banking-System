import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Tag, Space, message, Popconfirm } from 'antd';
import { UserAddOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { customerApi } from '../api/bankApi';

export default function Customers() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [branch, setBranch] = useState('HN');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form] = Form.useForm();

  const loadCustomers = async () => {
    setLoading(true);
    try {
      const res = await customerApi.getByBranch(branch);
      setCustomers(res.data.data || []);
    } catch (err) {
      message.error('Lỗi tải dữ liệu: ' + (err.response?.data?.message || err.message));
    }
    setLoading(false);
  };

  useEffect(() => { loadCustomers(); }, [branch]);

  const handleSubmit = async (values) => {
    try {
      if (editingId) {
        await customerApi.update(editingId, { ...values, branchId: branch });
        message.success('Cập nhật thành công!');
      } else {
        await customerApi.create(values);
        message.success('Tạo khách hàng thành công!');
      }
      setModalOpen(false);
      form.resetFields();
      setEditingId(null);
      loadCustomers();
    } catch (err) {
      message.error('Lỗi: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleEdit = (record) => {
    setEditingId(record.customerId);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleDelete = async (id) => {
    try {
      await customerApi.delete(id, branch);
      message.success('Xóa thành công!');
      loadCustomers();
    } catch (err) {
      message.error('Lỗi xóa: ' + (err.response?.data?.message || err.message));
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'customerId', key: 'id', width: 60 },
    { title: 'Họ tên', dataIndex: 'fullName', key: 'name', render: (v) => <strong>{v}</strong> },
    { title: 'SĐT', dataIndex: 'phone', key: 'phone' },
    { title: 'Email', dataIndex: 'email', key: 'email', render: (v) => v || '-' },
    { title: 'Địa chỉ', dataIndex: 'address', key: 'address', ellipsis: true },
    {
      title: 'Chi nhánh', dataIndex: 'branchId', key: 'branch',
      render: (v) => <Tag color="blue">{v}</Tag>
    },
    {
      title: 'Thao tác', key: 'actions', width: 140,
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          <Popconfirm title="Xóa khách hàng này?" onConfirm={() => handleDelete(record.customerId)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Quản lý Khách hàng</h2>
      </div>

      <Card>
        <Space style={{ marginBottom: 16 }}>
          <Select value={branch} onChange={setBranch} style={{ width: 160 }}
            options={[
              { value: 'HN', label: 'Hà Nội' },
              { value: 'DN', label: 'Đà Nẵng' },
              { value: 'HCM', label: 'TP.HCM' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={loadCustomers}>Tải lại</Button>
          <Button type="primary" icon={<UserAddOutlined />}
            onClick={() => { setEditingId(null); form.resetFields(); form.setFieldsValue({ branchId: branch }); setModalOpen(true); }}>
            Thêm khách hàng
          </Button>
        </Space>
        <Table loading={loading} dataSource={customers} columns={columns}
          rowKey="customerId" pagination={{ pageSize: 10 }} />
      </Card>

      <Modal
        title={editingId ? 'Sửa khách hàng' : 'Thêm khách hàng mới'}
        open={modalOpen}
        onCancel={() => { setModalOpen(false); setEditingId(null); }}
        onOk={() => form.submit()}
        okText={editingId ? 'Cập nhật' : 'Tạo mới'}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true }]}>
            <Input placeholder="Nguyễn Văn A" />
          </Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true }]}>
            <Input placeholder="0901234567" />
          </Form.Item>
          <Form.Item name="email" label="Email">
            <Input placeholder="email@example.com" />
          </Form.Item>
          <Form.Item name="address" label="Địa chỉ">
            <Input placeholder="123 Đường ABC, Quận XYZ" />
          </Form.Item>
          <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true }]}>
            <Select options={[
              { value: 'HN', label: 'Hà Nội' },
              { value: 'DN', label: 'Đà Nẵng' },
              { value: 'HCM', label: 'TP.HCM' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
