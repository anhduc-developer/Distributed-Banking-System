import React, { useEffect, useState } from 'react';
import { Card, Table, Tag, Spin } from 'antd';
import { BankOutlined, EnvironmentOutlined } from '@ant-design/icons';
import { branchApi } from '../api/bankApi';

export default function Branches() {
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    branchApi.getAll()
      .then(res => setBranches(res.data.data || []))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const columns = [
    {
      title: 'Mã CN', dataIndex: 'branchId', key: 'branchId',
      render: (v) => <Tag color="blue" style={{ fontWeight: 600 }}>{v}</Tag>,
    },
    {
      title: 'Tên chi nhánh', dataIndex: 'branchName', key: 'branchName',
      render: (v) => <><div style={{ marginRight: 8 }} />{v}</>
    },
    {
      title: 'Thành phố', dataIndex: 'city', key: 'city',
      render: (v) => <><div style={{ marginRight: 8, color: '#fa8c16' }} />{v}</>
    },
    {
      title: 'Ngày tạo', dataIndex: 'createdAt', key: 'createdAt',
      render: (v) => v ? new Date(v).toLocaleDateString('vi-VN') : '-'
    },
  ];

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Quản lý Chi nhánh</h2>
      </div>
      <Card>
        <Table
          loading={loading}
          dataSource={branches}
          columns={columns}
          rowKey="branchId"
          pagination={false}
        />
      </Card>
    </div>
  );
}
