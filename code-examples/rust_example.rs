// Blockchain Smart Contract Simulator
// Author: Rasya Andrean
// Description: High-performance blockchain simulation with Rust

use std::collections::HashMap;
use std::sync::{Arc, Mutex, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};
use std::thread;
use std::sync::mpsc;
use serde::{Deserialize, Serialize};
use sha2::{Sha256, Digest};
use tokio::time::{sleep, Duration};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Transaction {
    pub id: String,
    pub from: String,
    pub to: String,
    pub amount: u64,
    pub timestamp: u64,
    pub signature: String,
    pub gas_fee: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Block {
    pub index: u64,
    pub timestamp: u64,
    pub transactions: Vec<Transaction>,
    pub previous_hash: String,
    pub hash: String,
    pub nonce: u64,
    pub merkle_root: String,
    pub validator: String,
}

#[derive(Debug, Clone)]
pub struct Account {
    pub address: String,
    pub balance: u64,
    pub nonce: u64,
    pub contract_code: Option<String>,
    pub storage: HashMap<String, String>,
}

#[derive(Debug)]
pub struct BlockchainState {
    pub accounts: RwLock<HashMap<String, Account>>,
    pub pending_transactions: Mutex<Vec<Transaction>>,
    pub transaction_pool: Mutex<HashMap<String, Transaction>>,
}

#[derive(Debug)]
pub struct Blockchain {
    pub chain: RwLock<Vec<Block>>,
    pub state: Arc<BlockchainState>,
    pub difficulty: u32,
    pub block_time: Duration,
    pub validators: RwLock<Vec<String>>,
    pub consensus: ConsensusType,
}

#[derive(Debug, Clone)]
pub enum ConsensusType {
    ProofOfWork,
    ProofOfStake,
    DelegatedProofOfStake,
}

#[derive(Debug)]
pub struct SmartContract {
    pub address: String,
    pub code: String,
    pub abi: Vec<ContractFunction>,
    pub storage: HashMap<String, String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContractFunction {
    pub name: String,
    pub inputs: Vec<String>,
    pub outputs: Vec<String>,
    pub payable: bool,
}

#[derive(Debug)]
pub struct VirtualMachine {
    pub stack: Vec<u64>,
    pub memory: Vec<u8>,
    pub gas_used: u64,
    pub gas_limit: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityEvent {
    pub event_type: String,
    pub timestamp: u64,
    pub details: String,
    pub severity: String,
    pub source: String,
}

#[derive(Debug)]
pub struct SecurityMonitor {
    pub events: RwLock<Vec<SecurityEvent>>,
    pub threat_level: RwLock<u32>, // 0-100 scale
}

impl SecurityMonitor {
    pub fn new() -> Self {
        SecurityMonitor {
            events: RwLock::new(Vec::new()),
            threat_level: RwLock::new(0),
        }
    }

    pub fn log_event(&self, event_type: String, details: String, severity: String, source: String) {
        let event = SecurityEvent {
            event_type,
            timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
            details,
            severity: severity.clone(),
            source,
        };

        {
            let mut events = self.events.write().unwrap();
            events.push(event);

            // Keep only last 1000 events
            if events.len() > 1000 {
                events.drain(0..events.len()-1000);
            }
        }

        // Update threat level based on severity
        let severity_score = match severity.as_str() {
            "critical" => 20,
            "high" => 10,
            "medium" => 5,
            "low" => 1,
            _ => 0,
        };

        let mut threat_level = self.threat_level.write().unwrap();
        *threat_level = (*threat_level + severity_score).min(100);
    }

    pub fn get_threat_level(&self) -> u32 {
        *self.threat_level.read().unwrap()
    }

    pub fn reset_threat_level(&self) {
        let mut threat_level = self.threat_level.write().unwrap();
        *threat_level = 0;
    }
}

impl Transaction {
    pub fn new(from: String, to: String, amount: u64, gas_fee: u64) -> Self {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        let id = format!("{}-{}-{}-{}", from, to, amount, timestamp);
        let signature = Self::sign_transaction(&id, &from);

        Transaction {
            id,
            from,
            to,
            amount,
            timestamp,
            signature,
            gas_fee,
        }
    }

    fn sign_transaction(transaction_id: &str, private_key: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(format!("{}{}", transaction_id, private_key));
        format!("{:x}", hasher.finalize())
    }

    pub fn verify_signature(&self) -> bool {
        let expected_signature = Self::sign_transaction(&self.id, &self.from);
        self.signature == expected_signature
    }

    pub fn calculate_hash(&self) -> String {
        let mut hasher = Sha256::new();
        hasher.update(serde_json::to_string(self).unwrap());
        format!("{:x}", hasher.finalize())
    }
}

impl Block {
    pub fn new(
        index: u64,
        transactions: Vec<Transaction>,
        previous_hash: String,
        validator: String,
    ) -> Self {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        let merkle_root = Self::calculate_merkle_root(&transactions);

        let mut block = Block {
            index,
            timestamp,
            transactions,
            previous_hash,
            hash: String::new(),
            nonce: 0,
            merkle_root,
            validator,
        };

        block.hash = block.calculate_hash();
        block
    }

    pub fn calculate_hash(&self) -> String {
        let mut hasher = Sha256::new();
        let block_data = format!(
            "{}{}{}{}{}{}",
            self.index,
            self.timestamp,
            self.previous_hash,
            self.merkle_root,
            self.nonce,
            self.validator
        );
        hasher.update(block_data);
        format!("{:x}", hasher.finalize())
    }

    fn calculate_merkle_root(transactions: &[Transaction]) -> String {
        if transactions.is_empty() {
            return String::from("0");
        }

        let mut hashes: Vec<String> = transactions
            .iter()
            .map(|tx| tx.calculate_hash())
            .collect();

        while hashes.len() > 1 {
            let mut next_level = Vec::new();

            for chunk in hashes.chunks(2) {
                let mut hasher = Sha256::new();
                hasher.update(&chunk[0]);
                if chunk.len() > 1 {
                    hasher.update(&chunk[1]);
                } else {
                    hasher.update(&chunk[0]); // Duplicate if odd number
                }
                next_level.push(format!("{:x}", hasher.finalize()));
            }

            hashes = next_level;
        }

        hashes[0].clone()
    }

    pub fn mine_block(&mut self, difficulty: u32) -> Result<(), String> {
        let target = "0".repeat(difficulty as usize);

        loop {
            self.hash = self.calculate_hash();

            if self.hash.starts_with(&target) {
                println!("Block mined: {}", self.hash);
                return Ok(());
            }

            self.nonce += 1;

            // Prevent infinite loop in case of very high difficulty
            if self.nonce > 1_000_000 {
                return Err("Mining timeout".to_string());
            }
        }
    }
}

impl Account {
    pub fn new(address: String, initial_balance: u64) -> Self {
        Account {
            address,
            balance: initial_balance,
            nonce: 0,
            contract_code: None,
            storage: HashMap::new(),
        }
    }

    pub fn is_contract(&self) -> bool {
        self.contract_code.is_some()
    }

    pub fn deploy_contract(&mut self, code: String) {
        self.contract_code = Some(code);
    }
}

impl BlockchainState {
    pub fn new() -> Self {
        BlockchainState {
            accounts: RwLock::new(HashMap::new()),
            pending_transactions: Mutex::new(Vec::new()),
            transaction_pool: Mutex::new(HashMap::new()),
        }
    }

    pub fn create_account(&self, address: String, initial_balance: u64) {
        let mut accounts = self.accounts.write().unwrap();
        accounts.insert(address.clone(), Account::new(address, initial_balance));
    }

    pub fn get_balance(&self, address: &str) -> Option<u64> {
        let accounts = self.accounts.read().unwrap();
        accounts.get(address).map(|account| account.balance)
    }

    pub fn transfer(&self, from: &str, to: &str, amount: u64) -> Result<(), String> {
        let mut accounts = self.accounts.write().unwrap();

        // Check sender balance
        let sender = accounts.get_mut(from).ok_or("Sender not found")?;
        if sender.balance < amount {
            return Err("Insufficient balance".to_string());
        }

        // Deduct from sender
        sender.balance -= amount;
        sender.nonce += 1;

        // Add to receiver
        let receiver = accounts.get_mut(to).ok_or("Receiver not found")?;
        receiver.balance += amount;

        Ok(())
    }

    pub fn add_pending_transaction(&self, transaction: Transaction) {
        let mut pending = self.pending_transactions.lock().unwrap();
        pending.push(transaction);
    }

    pub fn get_pending_transactions(&self, limit: usize) -> Vec<Transaction> {
        let mut pending = self.pending_transactions.lock().unwrap();
        let transactions = pending.drain(..std::cmp::min(limit, pending.len())).collect();
        transactions
    }
}

impl Blockchain {
    pub fn new(consensus: ConsensusType, difficulty: u32) -> Self {
        let genesis_block = Block::new(
            0,
            vec![],
            String::from("0"),
            String::from("genesis"),
        );

        let mut chain = vec![genesis_block];
        chain[0].hash = chain[0].calculate_hash();

        Blockchain {
            chain: RwLock::new(chain),
            state: Arc::new(BlockchainState::new()),
            difficulty,
            block_time: Duration::from_secs(10),
            validators: RwLock::new(vec![]),
            consensus,
        }
    }

    pub fn add_validator(&self, validator: String) {
        let mut validators = self.validators.write().unwrap();
        validators.push(validator);
    }

    pub fn get_latest_block(&self) -> Block {
        let chain = self.chain.read().unwrap();
        chain.last().unwrap().clone()
    }

    pub fn add_block(&self, mut block: Block) -> Result<(), String> {
        let latest_block = self.get_latest_block();

        // Validate block
        if block.index != latest_block.index + 1 {
            return Err("Invalid block index".to_string());
        }

        if block.previous_hash != latest_block.hash {
            return Err("Invalid previous hash".to_string());
        }

        // Mine block based on consensus
        match self.consensus {
            ConsensusType::ProofOfWork => {
                block.mine_block(self.difficulty)?;
            }
            ConsensusType::ProofOfStake => {
                // Simplified PoS validation
                let validators = self.validators.read().unwrap();
                if !validators.contains(&block.validator) {
                    return Err("Invalid validator".to_string());
                }
            }
            ConsensusType::DelegatedProofOfStake => {
                // Simplified DPoS validation
                let validators = self.validators.read().unwrap();
                if validators.is_empty() || !validators.contains(&block.validator) {
                    return Err("Invalid delegate".to_string());
                }
            }
        }

        // Execute transactions
        for transaction in &block.transactions {
            if !transaction.verify_signature() {
                return Err("Invalid transaction signature".to_string());
            }

            self.state.transfer(
                &transaction.from,
                &transaction.to,
                transaction.amount,
            )?;
        }

        // Add block to chain
        let mut chain = self.chain.write().unwrap();
        chain.push(block);

        Ok(())
    }

    pub fn create_block(&self, validator: String) -> Result<Block, String> {
        let latest_block = self.get_latest_block();
        let transactions = self.state.get_pending_transactions(100);

        let block = Block::new(
            latest_block.index + 1,
            transactions,
            latest_block.hash,
            validator,
        );

        Ok(block)
    }

    pub fn get_chain_length(&self) -> usize {
        let chain = self.chain.read().unwrap();
        chain.len()
    }

    pub fn validate_chain(&self) -> bool {
        let chain = self.chain.read().unwrap();

        for i in 1..chain.len() {
            let current_block = &chain[i];
            let previous_block = &chain[i - 1];

            // Validate hash
            if current_block.hash != current_block.calculate_hash() {
                return false;
            }

            // Validate previous hash
            if current_block.previous_hash != previous_block.hash {
                return false;
            }

            // Validate index
            if current_block.index != previous_block.index + 1 {
                return false;
            }
        }

        true
    }
}

impl VirtualMachine {
    pub fn new(gas_limit: u64) -> Self {
        VirtualMachine {
            stack: Vec::new(),
            memory: vec![0; 1024], // 1KB memory
            gas_used: 0,
            gas_limit,
        }
    }

    pub fn push(&mut self, value: u64) -> Result<(), String> {
        if self.gas_used + 1 > self.gas_limit {
            return Err("Out of gas".to_string());
        }

        self.stack.push(value);
        self.gas_used += 1;
        Ok(())
    }

    pub fn pop(&mut self) -> Result<u64, String> {
        if self.gas_used + 1 > self.gas_limit {
            return Err("Out of gas".to_string());
        }

        if self.stack.is_empty() {
            return Err("Stack underflow".to_string());
        }

        self.gas_used += 1;
        Ok(self.stack.pop().unwrap())
    }

    pub fn execute_opcode(&mut self, opcode: &str) -> Result<(), String> {
        if self.gas_used + 5 > self.gas_limit {
            return Err("Out of gas".to_string());
        }

        match opcode {
            "ADD" => {
                let a = self.pop()?;
                let b = self.pop()?;
                self.push(a + b)?;
            }
            "SUB" => {
                let a = self.pop()?;
                let b = self.pop()?;
                self.push(a - b)?;
            }
            "MUL" => {
                let a = self.pop()?;
                let b = self.pop()?;
                self.push(a * b)?;
            }
            "DIV" => {
                let a = self.pop()?;
                let b = self.pop()?;
                if b == 0 {
                    return Err("Division by zero".to_string());
                }
                self.push(a / b)?;
            }
            _ => return Err(format!("Unknown opcode: {}", opcode)),
        }

        self.gas_used += 5;
        Ok(())
    }

    // Enhanced security check for contract execution
    pub fn security_check(&self) -> Result<(), String> {
        // Check for dangerous operations
        if self.memory.iter().any(|&x| x == 0xFF) {
            return Err("Security violation: forbidden memory access".to_string());
        }

        // Check gas usage
        if self.gas_used > self.gas_limit * 90 / 100 {
            return Err("Security warning: high gas usage".to_string());
        }

        Ok(())
    }
}

impl SmartContract {
    pub fn new(address: String, code: String) -> Self {
        SmartContract {
            address,
            code,
            abi: vec![
                ContractFunction {
                    name: "transfer".to_string(),
                    inputs: vec!["from".to_string(), "to".to_string()],
                    outputs: vec!["success".to_string()],
                    payable: true,
                },
                ContractFunction {
                    name: "balance".to_string(),
                    inputs: vec!["address".to_string()],
                    outputs: vec!["amount".to_string()],
                    payable: false,
                },
                ContractFunction {
                    name: "mint".to_string(),
                    inputs: vec!["to".to_string(), "amount".to_string()],
                    outputs: vec!["success".to_string()],
                    payable: false,
                },
            ],
            storage: HashMap::new(),
        }
    }

    pub fn call_function(&self, vm: &mut VirtualMachine, function_name: &str, args: Vec<u64>) -> Result<String, String> {
        // Security check before execution
        vm.security_check()?;

        match function_name {
            "transfer" => {
                if args.len() < 2 {
                    return Err("Insufficient arguments for transfer".to_string());
                }

                let from = args[0];
                let to = args[1];
                let amount = if args.len() > 2 { args[2] } else { 0 };

                // Execute transfer logic
                vm.push(from)?;
                vm.push(to)?;
                vm.push(amount)?;
                vm.execute_opcode("ADD")?;

                Ok(format!("Transferred {} from {} to {}", amount, from, to))
            }
            "balance" => {
                if args.is_empty() {
                    return Err("Insufficient arguments for balance".to_string());
                }

                let address = args[0];
                // In a real implementation, this would check actual balance
                vm.push(address)?;
                Ok(format!("Balance for address {}: 1000", address))
            }
            "mint" => {
                if args.len() < 2 {
                    return Err("Insufficient arguments for mint".to_string());
                }

                let to = args[0];
                let amount = args[1];

                // Execute mint logic
                vm.push(to)?;
                vm.push(amount)?;
                vm.execute_opcode("ADD")?;

                Ok(format!("Minted {} to address {}", amount, to))
            }
            _ => Err(format!("Unknown function: {}", function_name)),
        }
    }

    // Enhanced function to detect potential security issues in contract code
    pub fn analyze_security(&self) -> Vec<SecurityEvent> {
        let mut issues = Vec::new();

        // Check for reentrancy vulnerabilities
        if self.code.contains("call") && self.code.contains("transfer") {
            issues.push(SecurityEvent {
                event_type: "reentrancy_risk".to_string(),
                timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
                details: "Potential reentrancy vulnerability detected".to_string(),
                severity: "high".to_string(),
                source: self.address.clone(),
            });
        }

        // Check for integer overflow risks
        if self.code.contains("+") || self.code.contains("*") {
            issues.push(SecurityEvent {
                event_type: "overflow_risk".to_string(),
                timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
                details: "Potential integer overflow risk detected".to_string(),
                severity: "medium".to_string(),
                source: self.address.clone(),
            });
        }

        // Check for unsafe external calls
        if self.code.contains("delegatecall") {
            issues.push(SecurityEvent {
                event_type: "unsafe_call".to_string(),
                timestamp: SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
                details: "Unsafe delegatecall detected".to_string(),
                severity: "critical".to_string(),
                source: self.address.clone(),
            });
        }

        issues
    }
}

// Network simulation
pub struct NetworkNode {
    pub id: String,
    pub blockchain: Arc<Blockchain>,
    pub peers: Vec<String>,
    pub is_validator: bool,
}

impl NetworkNode {
    pub fn new(id: String, blockchain: Arc<Blockchain>, is_validator: bool) -> Self {
        NetworkNode {
            id,
            blockchain,
            peers: Vec::new(),
            is_validator,
        }
    }

    pub fn connect_peer(&mut self, peer_id: String) {
        if !self.peers.contains(&peer_id) {
            self.peers.push(peer_id);
        }
    }

    pub async fn start_mining(&self) -> Result<(), String> {
        if !self.is_validator {
            return Err("Node is not a validator".to_string());
        }

        loop {
            sleep(self.blockchain.block_time).await;

            let block = self.blockchain.create_block(self.id.clone())?;

            match self.blockchain.add_block(block) {
                Ok(_) => {
                    println!("Node {} mined a new block", self.id);
                }
                Err(e) => {
                    println!("Node {} failed to mine block: {}", self.id, e);
                }
            }
        }
    }

    pub fn broadcast_transaction(&self, transaction: Transaction) {
        self.blockchain.state.add_pending_transaction(transaction);
        println!("Node {} broadcasted transaction", self.id);
    }
}

// Example usage and testing
#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("🚀 Starting Enhanced Blockchain Simulator");

    // Create blockchain
    let blockchain = Arc::new(Blockchain::new(ConsensusType::ProofOfWork, 2));

    // Create security monitor
    let security_monitor = Arc::new(SecurityMonitor::new());

    // Create accounts
    blockchain.state.create_account("alice".to_string(), 1000);
    blockchain.state.create_account("bob".to_string(), 500);
    blockchain.state.create_account("charlie".to_string(), 750);

    // Add validators
    blockchain.add_validator("validator1".to_string());
    blockchain.add_validator("validator2".to_string());

    // Create network nodes
    let mut node1 = NetworkNode::new("validator1".to_string(), blockchain.clone(), true);
    let mut node2 = NetworkNode::new("validator2".to_string(), blockchain.clone(), true);
    let mut node3 = NetworkNode::new("node3".to_string(), blockchain.clone(), false);

    // Connect peers
    node1.connect_peer("validator2".to_string());
    node1.connect_peer("node3".to_string());
    node2.connect_peer("validator1".to_string());
    node2.connect_peer("node3".to_string());
    node3.connect_peer("validator1".to_string());
    node3.connect_peer("validator2".to_string());

    // Create and broadcast transactions
    let tx1 = Transaction::new("alice".to_string(), "bob".to_string(), 100, 10);
    let tx2 = Transaction::new("bob".to_string(), "charlie".to_string(), 50, 5);
    let tx3 = Transaction::new("charlie".to_string(), "alice".to_string(), 25, 3);

    node1.broadcast_transaction(tx1);
    node2.broadcast_transaction(tx2);
    node3.broadcast_transaction(tx3);

    // Deploy smart contract with security analysis
    let contract = SmartContract::new(
        "contract1".to_string(),
        "contract code with potential reentrancy call transfer".to_string(),
    );

    // Analyze contract security
    let security_issues = contract.analyze_security();
    if !security_issues.is_empty() {
        println!("\n⚠️  Security Issues Detected in Smart Contract:");
        for issue in &security_issues {
            println!("  {}: {} (Severity: {})",
                issue.event_type, issue.details, issue.severity);
            security_monitor.log_event(
                issue.event_type.clone(),
                issue.details.clone(),
                issue.severity.clone(),
                issue.source.clone(),
            );
        }
    }

    let mut vm = VirtualMachine::new(10000);

    // Execute contract functions with security monitoring
    println!("\n📋 Executing smart contract functions:");

    match contract.call_function(&mut vm, "transfer", vec![123, 500]) {
        Ok(result) => println!("Transfer result: {:?}", result),
        Err(e) => {
            println!("Transfer error: {}", e);
            security_monitor.log_event(
                "contract_execution_error".to_string(),
                e.clone(),
                "medium".to_string(),
                "contract1".to_string(),
            );
        }
    }

    match contract.call_function(&mut vm, "balance", vec![123]) {
        Ok(result) => println!("Balance result: {:?}", result),
        Err(e) => {
            println!("Balance error: {}", e);
            security_monitor.log_event(
                "contract_execution_error".to_string(),
                e.clone(),
                "low".to_string(),
                "contract1".to_string(),
            );
        }
    }

    // Print security monitor status
    println!("\n🛡️  Security Monitor Status:");
    println!("Current threat level: {}", security_monitor.get_threat_level());

    // Print initial state
    println!("\n💰 Initial Balances:");
    println!("Alice: {}", blockchain.state.get_balance("alice").unwrap_or(0));
    println!("Bob: {}", blockchain.state.get_balance("bob").unwrap_or(0));
    println!("Charlie: {}", blockchain.state.get_balance("charlie").unwrap_or(0));

    // Mine some blocks
    println!("\n⛏️  Mining blocks...");

    for i in 0..3 {
        let block = blockchain.create_block(format!("validator{}", (i % 2) + 1))?;

        match blockchain.add_block(block) {
            Ok(_) => println!("Block {} added successfully", i + 1),
            Err(e) => {
                println!("Failed to add block {}: {}", i + 1, e);
                security_monitor.log_event(
                    "block_mining_error".to_string(),
                    e.clone(),
                    "high".to_string(),
                    format!("validator{}", (i % 2) + 1),
                );
            }
        }

        sleep(Duration::from_millis(100)).await;
    }

    // Print final state
    println!("\n💰 Final Balances:");
    println!("Alice: {}", blockchain.state.get_balance("alice").unwrap_or(0));
    println!("Bob: {}", blockchain.state.get_balance("bob").unwrap_or(0));
    println!("Charlie: {}", blockchain.state.get_balance("charlie").unwrap_or(0));

    // Validate blockchain
    println!("\n🔍 Blockchain validation: {}", blockchain.validate_chain());
    println!("📊 Chain length: {}", blockchain.get_chain_length());

    // Print blockchain info
    println!("\n⛓️  Blockchain Info:");
    println!("Consensus: {:?}", blockchain.consensus);
    println!("Difficulty: {}", blockchain.difficulty);
    println!("Block time: {:?}", blockchain.block_time);

    // Print final security status
    println!("\n🛡️  Final Security Status:");
    println!("Threat level: {}", security_monitor.get_threat_level());
    {
        let events = security_monitor.events.read().unwrap();
        if !events.is_empty() {
            println!("Security events detected: {}", events.len());
            // Show last 5 events
            for event in events.iter().rev().take(5) {
                println!("  {}: {} ({})",
                    event.event_type, event.details, event.severity);
            }
        } else {
            println!("No security events detected");
        }
    }

    // Start mining in background (commented out to prevent infinite loop)
    // tokio::spawn(async move {
    //     if let Err(e) = node1.start_mining().await {
    //         println!("Mining error: {}", e);
    //     }
    // });

    println!("\n✅ Enhanced Blockchain simulation completed successfully!");

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_transaction_creation() {
        let tx = Transaction::new("alice".to_string(), "bob".to_string(), 100, 10);
        assert_eq!(tx.from, "alice");
        assert_eq!(tx.to, "bob");
        assert_eq!(tx.amount, 100);
        assert_eq!(tx.gas_fee, 10);
        assert!(tx.verify_signature());
    }

    #[test]
    fn test_block_creation() {
        let transactions = vec![
            Transaction::new("alice".to_string(), "bob".to_string(), 100, 10),
        ];

        let block = Block::new(1, transactions, "previous_hash".to_string(), "validator".to_string());
        assert_eq!(block.index, 1);
        assert_eq!(block.previous_hash, "previous_hash");
        assert_eq!(block.validator, "validator");
        assert_eq!(block.transactions.len(), 1);
    }

    #[test]
    fn test_blockchain_validation() {
        let blockchain = Blockchain::new(ConsensusType::ProofOfWork, 1);
        assert!(blockchain.validate_chain());

        blockchain.state.create_account("alice".to_string(), 1000);
        blockchain.state.create_account("bob".to_string(), 500);

        let tx = Transaction::new("alice".to_string(), "bob".to_string(), 100, 10);
        blockchain.state.add_pending_transaction(tx);

        let block = blockchain.create_block("validator".to_string()).unwrap();
        assert!(blockchain.add_block(block).is_ok());
        assert!(blockchain.validate_chain());
    }

    #[test]
    fn test_smart_contract_execution() {
        let contract = SmartContract::new("contract1".to_string(), "code".to_string());
        let mut vm = VirtualMachine::new(10000);

        let result = contract.call_function(&mut vm, "transfer", vec![123, 500]);
        assert!(result.is_ok());

        let result = contract.call_function(&mut vm, "balance", vec![123]);
        assert!(result.is_ok());

        let result = contract.call_function(&mut vm, "unknown", vec![]);
        assert!(result.is_err());
    }
}
