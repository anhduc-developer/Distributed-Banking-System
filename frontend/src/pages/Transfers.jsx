import React, { useState } from 'react';
import { Card, Form, InputNumber, Select, Button, message, Descriptions, Tag, Row, Col, Result, Divider, Steps, Alert, Switch, Typography } from 'antd';
import { SwapOutlined, NodeIndexOutlined, CheckCircleOutlined, CloseCircleOutlined, ThunderboltOutlined, WarningOutlined } from '@ant-design/icons';
import { transferApi } from '../api/bankApi';

const { Text } = Typography;
const fmt = (v) => v ? Number(v).toLocaleString('vi-VN') : '0';

export default function Transfers() {
  const [internalForm] = Form.useForm();
  const [interForm] = Form.useForm();
  const [internalResult, setInternalResult] = useState(null);
  const [interResult, setInterResult] = useState(null);
  const [internalLoading, setInternalLoading] = useState(false);
  const [interLoading, setInterLoading] = useState(false);
  const [simulateCrash, setSimulateCrash] = useState(false);

  const handleInternal = async (values) => {
    setInternalLoading(true);
    setInternalResult(null);
    try {
      const res = await transferApi.internal(values);
      setInternalResult(res.data.success ? res.data.data : { status: 'FAILED', message: res.data.message });
      if (res.data.success) message.success('Chuyển tiền thành công!');
      else message.error(res.data.message);
    } catch (err) {
      const msg = err.response?.data?.message || err.message;
      setInternalResult({ status: 'FAILED', message: msg });
      message.error(msg);
    }
    setInternalLoading(false);
  };

  const handleInterBranch = async (values) => {
    setInterLoading(true);
    setInterResult(null);
    try {
      const res = await transferApi.interBranch({ ...values, simulateCrash });
      setInterResult(res.data.success ? res.data.data : { status: 'FAILED', message: res.data.message });
      if (res.data.success) message.success('Chuyển tiền liên chi nhánh thành công (2PC COMMITTED)!');
      else message.error(res.data.message);
    } catch (err) {
      const msg = err.response?.data?.message || err.message;
      setInterResult({ status: 'FAILED', message: msg });
      message.error(msg);
    }
    setInterLoading(false);
  };

  const renderResult = (result) => {
    if (!result) return null;
    const isSuccess = result.status === 'SUCCESS';

    return (
      <div style={{ marginTop: 16 }}>
        {isSuccess ? (
          <Card style={{ borderLeft: '4px solid #52c41a' }}>
            <Result status="success"
              title="Chuyển tiền thành công!"
              subTitle={result.transactionId && `Transaction ID: ${result.transactionId}`}
            />
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="Từ TK">#{result.fromAccountId}</Descriptions.Item>
              <Descriptions.Item label="Đến TK">#{result.toAccountId}</Descriptions.Item>
              <Descriptions.Item label="Từ CN"><Tag color="blue">{result.fromBranch}</Tag></Descriptions.Item>
              <Descriptions.Item label="Đến CN"><Tag color="green">{result.toBranch}</Tag></Descriptions.Item>
              <Descriptions.Item label="Số tiền"><strong>{fmt(result.amount)} ₫</strong></Descriptions.Item>
              <Descriptions.Item label="Trạng thái"><Tag color="green">SUCCESS</Tag></Descriptions.Item>
              <Descriptions.Item label="Số dư nguồn">{fmt(result.sourceBalanceAfter)} ₫</Descriptions.Item>
              <Descriptions.Item label="Số dư đích">{fmt(result.destBalanceAfter)} ₫</Descriptions.Item>
            </Descriptions>
          </Card>
        ) : (
          <Card style={{ borderLeft: '4px solid #ff4d4f' }}>
            <Result status="error" title="Chuyển tiền thất bại!" subTitle={result.message} />
          </Card>
        )}
      </div>
    );
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Chuyển tiền</h2>
      </div>

      <Row gutter={24}>
        {/* INTERNAL TRANSFER */}
        <Col xs={24} lg={12}>
          <Card
            title={<><SwapOutlined style={{ color: '#1677ff' }} /> Chuyển cùng chi nhánh</>}
            style={{ borderTop: '3px solid #1677ff', marginBottom: 24 }}
          >
            <Form form={internalForm} layout="vertical" onFinish={handleInternal}>
              <Form.Item name="branchId" label="Chi nhánh" rules={[{ required: true }]}>
                <Select placeholder="Chọn chi nhánh" options={[
                  { value: 'HN', label: 'Hà Nội' },
                  { value: 'DN', label: 'Đà Nẵng' },
                  { value: 'HCM', label: 'TP.HCM' },
                ]} />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="fromAccountId" label="Từ TK (ID)" rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={1} placeholder="ID nguồn" />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="toAccountId" label="Đến TK (ID)" rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={1} placeholder="ID đích" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="amount" label="Số tiền (VND)" rules={[{ required: true }]}>
                <InputNumber style={{ width: '100%' }} min={1000} step={100000} placeholder="1,000,000"
                  formatter={v => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={internalLoading} block>
                <SwapOutlined /> Chuyển tiền nội bộ
              </Button>
            </Form>
            {renderResult(internalResult)}
          </Card>
        </Col>

        {/* INTER-BRANCH TRANSFER — 2PC */}
        <Col xs={24} lg={12}>
          <Card
            title={<><NodeIndexOutlined style={{ color: '#722ed1' }} /> Chuyển liên chi nhánh (2PC)</>}
            style={{ borderTop: '3px solid #722ed1', marginBottom: 24 }}
          >
            <Form form={interForm} layout="vertical" onFinish={handleInterBranch}>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="fromBranch" label="Từ CN" rules={[{ required: true }]}>
                    <Select placeholder="CN nguồn" options={[
                      { value: 'HN', label: 'Hà Nội' },
                      { value: 'DN', label: 'Đà Nẵng' },
                      { value: 'HCM', label: 'TP.HCM' },
                    ]} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="fromAccountId" label="Từ TK (ID)" rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={1} placeholder="ID nguồn" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="toBranch" label="Đến CN" rules={[{ required: true }]}>
                    <Select placeholder="CN đích" options={[
                      { value: 'HN', label: 'Hà Nội' },
                      { value: 'DN', label: 'Đà Nẵng' },
                      { value: 'HCM', label: 'TP.HCM' },
                    ]} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="toAccountId" label="Đến TK (ID)" rules={[{ required: true }]}>
                    <InputNumber style={{ width: '100%' }} min={1} placeholder="ID đích" />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="amount" label="Số tiền (VND)" rules={[{ required: true }]}>
                <InputNumber style={{ width: '100%' }} min={1000} step={100000} placeholder="5,000,000"
                  formatter={v => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')} />
              </Form.Item>

              {/* SIMULATE CRASH TOGGLE */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '12px 16px',
                background: simulateCrash ? '#fff2f0' : '#f6ffed',
                borderRadius: 8,
                border: `1px solid ${simulateCrash ? '#ffccc7' : '#b7eb8f'}`,
                transition: 'all 0.3s',
                marginBottom: 16,
              }}>
                <div>
                  <Text strong style={{ color: simulateCrash ? '#cf1322' : '#389e0d', fontSize: 13 }}>
                    {simulateCrash
                      ? <><WarningOutlined /> 💥 Dest Server CRASH (ON)</>
                      : <><CheckCircleOutlined /> Hoạt động bình thường</>}
                  </Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {simulateCrash
                      ? 'Source sẽ trừ tiền → Dest crash → Rollback → Hoàn tiền'
                      : 'Chuyển tiền bình thường qua 2PC'}
                  </Text>
                </div>
                <Switch
                  checked={simulateCrash}
                  onChange={setSimulateCrash}
                  checkedChildren="CRASH"
                  unCheckedChildren="OK"
                  style={{ background: simulateCrash ? '#ff4d4f' : '#52c41a' }}
                />
              </div>

              <Button type="primary" htmlType="submit" loading={interLoading} block
                style={{ background: simulateCrash ? '#ff4d4f' : '#722ed1', borderColor: simulateCrash ? '#ff4d4f' : '#722ed1' }}>
                {simulateCrash
                  ? <><WarningOutlined /> Chuyển tiền (Giả lập CRASH)</>
                  : <><NodeIndexOutlined /> Chuyển liên chi nhánh (2PC)</>}
              </Button>
            </Form>
            {renderResult(interResult)}
          </Card>
        </Col>
      </Row>

    </div>
  );
}
